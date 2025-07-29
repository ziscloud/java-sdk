/*
 * Copyright 2024-2024 the original author or authors.
 */

package io.modelcontextprotocol.client.transport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.transport.ResponseSubscribers.ResponseEvent;
import io.modelcontextprotocol.spec.DefaultMcpTransportSession;
import io.modelcontextprotocol.spec.DefaultMcpTransportStream;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportSession;
import io.modelcontextprotocol.spec.McpTransportSessionNotFoundException;
import io.modelcontextprotocol.spec.McpTransportStream;
import io.modelcontextprotocol.util.Assert;
import io.modelcontextprotocol.util.Utils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * An implementation of the Streamable HTTP protocol as defined by the
 * <code>2025-03-26</code> version of the MCP specification.
 *
 * <p>
 * The transport is capable of resumability and reconnects. It reacts to transport-level
 * session invalidation and will propagate {@link McpTransportSessionNotFoundException
 * appropriate exceptions} to the higher level abstraction layer when needed in order to
 * allow proper state management. The implementation handles servers that are stateful and
 * provide session meta information, but can also communicate with stateless servers that
 * do not provide a session identifier and do not support SSE streams.
 * </p>
 * <p>
 * This implementation does not handle backwards compatibility with the <a href=
 * "https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse">"HTTP
 * with SSE" transport</a>. In order to communicate over the phased-out
 * <code>2024-11-05</code> protocol, use {@link HttpClientSseClientTransport} or
 * {@code WebFluxSseClientTransport}.
 * </p>
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http">Streamable
 * HTTP transport specification</a>
 */
public class HttpClientStreamableHttpTransport implements McpClientTransport {

	private static final Logger logger = LoggerFactory.getLogger(HttpClientStreamableHttpTransport.class);

	private static final String DEFAULT_ENDPOINT = "/mcp";

	/**
	 * HTTP client for sending messages to the server. Uses HTTP POST over the message
	 * endpoint
	 */
	private final HttpClient httpClient;

	/** HTTP request builder for building requests to send messages to the server */
	private final HttpRequest.Builder requestBuilder;

	/**
	 * Event type for JSON-RPC messages received through the SSE connection. The server
	 * sends messages with this event type to transmit JSON-RPC protocol data.
	 */
	private static final String MESSAGE_EVENT_TYPE = "message";

	private static final String APPLICATION_JSON = "application/json";

	private static final String TEXT_EVENT_STREAM = "text/event-stream";

	public static int NOT_FOUND = 404;

	public static int METHOD_NOT_ALLOWED = 405;

	public static int BAD_REQUEST = 400;

	private final ObjectMapper objectMapper;

	private final URI baseUri;

	private final String endpoint;

	private final boolean openConnectionOnStartup;

	private final boolean resumableStreams;

	private final AtomicReference<DefaultMcpTransportSession> activeSession = new AtomicReference<>();

	private final AtomicReference<Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>>> handler = new AtomicReference<>();

	private final AtomicReference<Consumer<Throwable>> exceptionHandler = new AtomicReference<>();

	private HttpClientStreamableHttpTransport(ObjectMapper objectMapper, HttpClient httpClient,
			HttpRequest.Builder requestBuilder, String baseUri, String endpoint, boolean resumableStreams,
			boolean openConnectionOnStartup) {
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		this.requestBuilder = requestBuilder;
		this.baseUri = URI.create(baseUri);
		this.endpoint = endpoint;
		this.resumableStreams = resumableStreams;
		this.openConnectionOnStartup = openConnectionOnStartup;
		this.activeSession.set(createTransportSession());
	}

	public static Builder builder(String baseUri) {
		return new Builder(baseUri);
	}

	@Override
	public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
		return Mono.deferContextual(ctx -> {
			this.handler.set(handler);
			if (this.openConnectionOnStartup) {
				logger.debug("Eagerly opening connection on startup");
				return this.reconnect(null).onErrorComplete(t -> {
					logger.warn("Eager connect failed ", t);
					return true;
				}).then();
			}
			return Mono.empty();
		});
	}

	private DefaultMcpTransportSession createTransportSession() {
		Function<String, Publisher<Void>> onClose = sessionId -> sessionId == null ? Mono.empty()
				: createDelete(sessionId);
		return new DefaultMcpTransportSession(onClose);
	}

	private Publisher<Void> createDelete(String sessionId) {
		HttpRequest request = this.requestBuilder.copy()
			.uri(Utils.resolveUri(this.baseUri, this.endpoint))
			.header("Cache-Control", "no-cache")
			.header("mcp-session-id", sessionId)
			.DELETE()
			.build();

		return Mono.fromFuture(() -> this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())).then();
	}

	@Override
	public void setExceptionHandler(Consumer<Throwable> handler) {
		logger.debug("Exception handler registered");
		this.exceptionHandler.set(handler);
	}

	private void handleException(Throwable t) {
		logger.debug("Handling exception for session {}", sessionIdOrPlaceholder(this.activeSession.get()), t);
		if (t instanceof McpTransportSessionNotFoundException) {
			McpTransportSession<?> invalidSession = this.activeSession.getAndSet(createTransportSession());
			logger.warn("Server does not recognize session {}. Invalidating.", invalidSession.sessionId());
			invalidSession.close();
		}
		Consumer<Throwable> handler = this.exceptionHandler.get();
		if (handler != null) {
			handler.accept(t);
		}
	}

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.defer(() -> {
			logger.debug("Graceful close triggered");
			DefaultMcpTransportSession currentSession = this.activeSession.getAndSet(createTransportSession());
			if (currentSession != null) {
				return currentSession.closeGracefully();
			}
			return Mono.empty();
		});
	}

	private Mono<Disposable> reconnect(McpTransportStream<Disposable> stream) {

		return Mono.deferContextual(ctx -> {

			if (stream != null) {
				logger.debug("Reconnecting stream {} with lastId {}", stream.streamId(), stream.lastId());
			}
			else {
				logger.debug("Reconnecting with no prior stream");
			}

			final AtomicReference<Disposable> disposableRef = new AtomicReference<>();
			final McpTransportSession<Disposable> transportSession = this.activeSession.get();

			HttpRequest.Builder requestBuilder = this.requestBuilder.copy();

			if (transportSession != null && transportSession.sessionId().isPresent()) {
				requestBuilder = requestBuilder.header("mcp-session-id", transportSession.sessionId().get());
			}

			if (stream != null && stream.lastId().isPresent()) {
				requestBuilder = requestBuilder.header("last-event-id", stream.lastId().get());
			}

			HttpRequest request = requestBuilder.uri(Utils.resolveUri(this.baseUri, this.endpoint))
				.header("Accept", TEXT_EVENT_STREAM)
				.header("Cache-Control", "no-cache")
				.GET()
				.build();

			Disposable connection = Flux.<ResponseEvent>create(sseSink -> this.httpClient
				.sendAsync(request, responseInfo -> ResponseSubscribers.sseToBodySubscriber(responseInfo, sseSink))
				.whenComplete((response, throwable) -> {
					if (throwable != null) {
						sseSink.error(throwable);
					}
					else {
						logger.debug("SSE connection established successfully");
					}
				}))
				.map(responseEvent -> (ResponseSubscribers.SseResponseEvent) responseEvent)
				.flatMap(responseEvent -> {
					int statusCode = responseEvent.responseInfo().statusCode();

					if (statusCode >= 200 && statusCode < 300) {

						if (MESSAGE_EVENT_TYPE.equals(responseEvent.sseEvent().event())) {
							try {
								// We don't support batching ATM and probably won't since
								// the
								// next version considers removing it.
								McpSchema.JSONRPCMessage message = McpSchema
									.deserializeJsonRpcMessage(this.objectMapper, responseEvent.sseEvent().data());

								Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> idWithMessages = Tuples
									.of(Optional.ofNullable(responseEvent.sseEvent().id()), List.of(message));

								McpTransportStream<Disposable> sessionStream = stream != null ? stream
										: new DefaultMcpTransportStream<>(this.resumableStreams, this::reconnect);
								logger.debug("Connected stream {}", sessionStream.streamId());

								return Flux.from(sessionStream.consumeSseStream(Flux.just(idWithMessages)));

							}
							catch (IOException ioException) {
								return Flux.<McpSchema.JSONRPCMessage>error(new McpError(
										"Error parsing JSON-RPC message: " + responseEvent.sseEvent().data()));
							}
						}
						else {
							logger.debug("Received SSE event with type: {}", responseEvent.sseEvent());
							return Flux.empty();
						}
					}
					else if (statusCode == METHOD_NOT_ALLOWED) { // NotAllowed
						logger.debug("The server does not support SSE streams, using request-response mode.");
						return Flux.empty();
					}
					else if (statusCode == NOT_FOUND) {
						String sessionIdRepresentation = sessionIdOrPlaceholder(transportSession);
						McpTransportSessionNotFoundException exception = new McpTransportSessionNotFoundException(
								"Session not found for session ID: " + sessionIdRepresentation);
						return Flux.<McpSchema.JSONRPCMessage>error(exception);
					}
					else if (statusCode == BAD_REQUEST) {
						String sessionIdRepresentation = sessionIdOrPlaceholder(transportSession);
						McpTransportSessionNotFoundException exception = new McpTransportSessionNotFoundException(
								"Session not found for session ID: " + sessionIdRepresentation);
						return Flux.<McpSchema.JSONRPCMessage>error(exception);
					}

					return Flux.<McpSchema.JSONRPCMessage>error(
							new McpError("Received unrecognized SSE event type: " + responseEvent.sseEvent().event()));

				}).<McpSchema
						.JSONRPCMessage>flatMap(jsonrpcMessage -> this.handler.get().apply(Mono.just(jsonrpcMessage)))
				.onErrorMap(CompletionException.class, t -> t.getCause())
				.onErrorComplete(t -> {
					this.handleException(t);
					return true;
				})
				.doFinally(s -> {
					Disposable ref = disposableRef.getAndSet(null);
					if (ref != null) {
						transportSession.removeConnection(ref);
					}
				})
				.contextWrite(ctx)
				.subscribe();

			disposableRef.set(connection);
			transportSession.addConnection(connection);
			return Mono.just(connection);
		});

	}

	private BodyHandler<Void> toSendMessageBodySubscriber(FluxSink<ResponseEvent> sink) {

		BodyHandler<Void> responseBodyHandler = responseInfo -> {

			String contentType = responseInfo.headers().firstValue("Content-Type").orElse("").toLowerCase();

			if (contentType.contains(TEXT_EVENT_STREAM)) {
				// For SSE streams, use line subscriber that returns Void
				logger.debug("Received SSE stream response, using line subscriber");
				return ResponseSubscribers.sseToBodySubscriber(responseInfo, sink);
			}
			else if (contentType.contains(APPLICATION_JSON)) {
				// For JSON responses and others, use string subscriber
				logger.debug("Received response, using string subscriber");
				return ResponseSubscribers.aggregateBodySubscriber(responseInfo, sink);
			}

			logger.debug("Received Bodyless response, using discarding subscriber");
			return ResponseSubscribers.bodilessBodySubscriber(responseInfo, sink);
		};

		return responseBodyHandler;

	}

	public String toString(McpSchema.JSONRPCMessage message) {
		try {
			return this.objectMapper.writeValueAsString(message);
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to serialize JSON-RPC message", e);
		}
	}

	public Mono<Void> sendMessage(McpSchema.JSONRPCMessage sentMessage) {
		return Mono.create(messageSink -> {
			logger.debug("Sending message {}", sentMessage);

			final AtomicReference<Disposable> disposableRef = new AtomicReference<>();
			final McpTransportSession<Disposable> transportSession = this.activeSession.get();

			HttpRequest.Builder requestBuilder = this.requestBuilder.copy();

			if (transportSession != null && transportSession.sessionId().isPresent()) {
				requestBuilder = requestBuilder.header("mcp-session-id", transportSession.sessionId().get());
			}

			String jsonBody = this.toString(sentMessage);

			HttpRequest request = requestBuilder.uri(Utils.resolveUri(this.baseUri, this.endpoint))
				.header("Accept", APPLICATION_JSON + ", " + TEXT_EVENT_STREAM)
				.header("Content-Type", APPLICATION_JSON)
				.header("Cache-Control", "no-cache")
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.build();

			Disposable connection = Flux.<ResponseEvent>create(responseEventSink -> {

				// Create the async request with proper body subscriber selection
				Mono.fromFuture(this.httpClient.sendAsync(request, this.toSendMessageBodySubscriber(responseEventSink))
					.whenComplete((response, throwable) -> {
						if (throwable != null) {
							responseEventSink.error(throwable);
						}
						else {
							logger.debug("SSE connection established successfully");
						}
					})).onErrorMap(CompletionException.class, t -> t.getCause()).onErrorComplete().subscribe();

			}).flatMap(responseEvent -> {
				if (transportSession.markInitialized(
						responseEvent.responseInfo().headers().firstValue("mcp-session-id").orElseGet(() -> null))) {
					// Once we have a session, we try to open an async stream for
					// the server to send notifications and requests out-of-band.

					reconnect(null).contextWrite(messageSink.contextView()).subscribe();
				}

				String sessionRepresentation = sessionIdOrPlaceholder(transportSession);

				int statusCode = responseEvent.responseInfo().statusCode();

				if (statusCode >= 200 && statusCode < 300) {

					String contentType = responseEvent.responseInfo()
						.headers()
						.firstValue("Content-Type")
						.orElse("")
						.toLowerCase();

					if (contentType.isBlank()) {
						logger.debug("No content type returned for POST in session {}", sessionRepresentation);
						// No content type means no response body, so we can just return
						// an empty stream
						messageSink.success();
						return Flux.empty();
					}
					else if (contentType.contains(TEXT_EVENT_STREAM)) {
						return Flux.just(((ResponseSubscribers.SseResponseEvent) responseEvent).sseEvent())
							.flatMap(sseEvent -> {
								try {
									// We don't support batching ATM and probably won't
									// since the
									// next version considers removing it.
									McpSchema.JSONRPCMessage message = McpSchema
										.deserializeJsonRpcMessage(this.objectMapper, sseEvent.data());

									Tuple2<Optional<String>, Iterable<McpSchema.JSONRPCMessage>> idWithMessages = Tuples
										.of(Optional.ofNullable(sseEvent.id()), List.of(message));

									McpTransportStream<Disposable> sessionStream = new DefaultMcpTransportStream<>(
											this.resumableStreams, this::reconnect);

									logger.debug("Connected stream {}", sessionStream.streamId());

									messageSink.success();

									return Flux.from(sessionStream.consumeSseStream(Flux.just(idWithMessages)));
								}
								catch (IOException ioException) {
									return Flux.<McpSchema.JSONRPCMessage>error(
											new McpError("Error parsing JSON-RPC message: " + sseEvent.data()));
								}
							});
					}
					else if (contentType.contains(APPLICATION_JSON)) {
						messageSink.success();
						String data = ((ResponseSubscribers.AggregateResponseEvent) responseEvent).data();
						if (sentMessage instanceof McpSchema.JSONRPCNotification && Utils.hasText(data)) {
							logger.warn("Notification: {} received non-compliant response: {}", sentMessage, data);
							return Mono.empty();
						}

						try {
							return Mono.just(McpSchema.deserializeJsonRpcMessage(objectMapper, data));
						}
						catch (IOException e) {
							// TODO: this should be a McpTransportError
							return Mono.error(e);
						}
					}
					logger.warn("Unknown media type {} returned for POST in session {}", contentType,
							sessionRepresentation);

					return Flux.<McpSchema.JSONRPCMessage>error(
							new RuntimeException("Unknown media type returned: " + contentType));
				}
				else if (statusCode == NOT_FOUND) {
					McpTransportSessionNotFoundException exception = new McpTransportSessionNotFoundException(
							"Session not found for session ID: " + sessionRepresentation);
					return Flux.<McpSchema.JSONRPCMessage>error(exception);
				}
				// Some implementations can return 400 when presented with a
				// session id that it doesn't know about, so we will
				// invalidate the session
				// https://github.com/modelcontextprotocol/typescript-sdk/issues/389
				else if (statusCode == BAD_REQUEST) {
					McpTransportSessionNotFoundException exception = new McpTransportSessionNotFoundException(
							"Session not found for session ID: " + sessionRepresentation);
					return Flux.<McpSchema.JSONRPCMessage>error(exception);
				}

				return Flux.<McpSchema.JSONRPCMessage>error(
						new RuntimeException("Failed to send message: " + responseEvent));
			})
				.flatMap(jsonRpcMessage -> this.handler.get().apply(Mono.just(jsonRpcMessage)))
				.onErrorMap(CompletionException.class, t -> t.getCause())
				.onErrorComplete(t -> {
					// handle the error first
					this.handleException(t);
					// inform the caller of sendMessage
					messageSink.error(t);
					return true;
				})
				.doFinally(s -> {
					logger.debug("SendMessage finally: {}", s);
					Disposable ref = disposableRef.getAndSet(null);
					if (ref != null) {
						transportSession.removeConnection(ref);
					}
				})
				.contextWrite(messageSink.contextView())
				.subscribe();

			disposableRef.set(connection);
			transportSession.addConnection(connection);
		});
	}

	private static String sessionIdOrPlaceholder(McpTransportSession<?> transportSession) {
		return transportSession.sessionId().orElse("[missing_session_id]");
	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

	/**
	 * Builder for {@link HttpClientStreamableHttpTransport}.
	 */
	public static class Builder {

		private final String baseUri;

		private ObjectMapper objectMapper;

		private HttpClient.Builder clientBuilder = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10));

		private String endpoint = DEFAULT_ENDPOINT;

		private boolean resumableStreams = true;

		private boolean openConnectionOnStartup = false;

		private HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

		/**
		 * Creates a new builder with the specified base URI.
		 * @param baseUri the base URI of the MCP server
		 */
		private Builder(String baseUri) {
			Assert.hasText(baseUri, "baseUri must not be empty");
			this.baseUri = baseUri;
		}

		/**
		 * Sets the HTTP client builder.
		 * @param clientBuilder the HTTP client builder
		 * @return this builder
		 */
		public Builder clientBuilder(HttpClient.Builder clientBuilder) {
			Assert.notNull(clientBuilder, "clientBuilder must not be null");
			this.clientBuilder = clientBuilder;
			return this;
		}

		/**
		 * Customizes the HTTP client builder.
		 * @param clientCustomizer the consumer to customize the HTTP client builder
		 * @return this builder
		 */
		public Builder customizeClient(final Consumer<HttpClient.Builder> clientCustomizer) {
			Assert.notNull(clientCustomizer, "clientCustomizer must not be null");
			clientCustomizer.accept(clientBuilder);
			return this;
		}

		/**
		 * Sets the HTTP request builder.
		 * @param requestBuilder the HTTP request builder
		 * @return this builder
		 */
		public Builder requestBuilder(HttpRequest.Builder requestBuilder) {
			Assert.notNull(requestBuilder, "requestBuilder must not be null");
			this.requestBuilder = requestBuilder;
			return this;
		}

		/**
		 * Customizes the HTTP client builder.
		 * @param requestCustomizer the consumer to customize the HTTP request builder
		 * @return this builder
		 */
		public Builder customizeRequest(final Consumer<HttpRequest.Builder> requestCustomizer) {
			Assert.notNull(requestCustomizer, "requestCustomizer must not be null");
			requestCustomizer.accept(requestBuilder);
			return this;
		}

		/**
		 * Configure the {@link ObjectMapper} to use.
		 * @param objectMapper instance to use
		 * @return the builder instance
		 */
		public Builder objectMapper(ObjectMapper objectMapper) {
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			this.objectMapper = objectMapper;
			return this;
		}

		/**
		 * Configure the endpoint to make HTTP requests against.
		 * @param endpoint endpoint to use
		 * @return the builder instance
		 */
		public Builder endpoint(String endpoint) {
			Assert.hasText(endpoint, "endpoint must be a non-empty String");
			this.endpoint = endpoint;
			return this;
		}

		/**
		 * Configure whether to use the stream resumability feature by keeping track of
		 * SSE event ids.
		 * @param resumableStreams if {@code true} event ids will be tracked and upon
		 * disconnection, the last seen id will be used upon reconnection as a header to
		 * resume consuming messages.
		 * @return the builder instance
		 */
		public Builder resumableStreams(boolean resumableStreams) {
			this.resumableStreams = resumableStreams;
			return this;
		}

		/**
		 * Configure whether the client should open an SSE connection upon startup. Not
		 * all servers support this (although it is in theory possible with the current
		 * specification), so use with caution. By default, this value is {@code false}.
		 * @param openConnectionOnStartup if {@code true} the {@link #connect(Function)}
		 * method call will try to open an SSE connection before sending any JSON-RPC
		 * request
		 * @return the builder instance
		 */
		public Builder openConnectionOnStartup(boolean openConnectionOnStartup) {
			this.openConnectionOnStartup = openConnectionOnStartup;
			return this;
		}

		/**
		 * Construct a fresh instance of {@link HttpClientStreamableHttpTransport} using
		 * the current builder configuration.
		 * @return a new instance of {@link HttpClientStreamableHttpTransport}
		 */
		public HttpClientStreamableHttpTransport build() {
			ObjectMapper objectMapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();

			return new HttpClientStreamableHttpTransport(objectMapper, clientBuilder.build(), requestBuilder, baseUri,
					endpoint, resumableStreams, openConnectionOnStartup);
		}

	}

}
