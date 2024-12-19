/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.server.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.JSONRPCMessage;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.util.Assert;

/**
 * Implementation of the MCP Stdio transport for servers that communicates using standard
 * input/output streams. Messages are exchanged as newline-delimited JSON-RPC messages
 * over stdin/stdout, with errors and debug information sent to stderr.
 *
 * @author Christian Tzolov
 */
public class StdioServerTransport implements McpTransport {

	private static final Logger logger = LoggerFactory.getLogger(StdioServerTransport.class);

	private final Sinks.Many<JSONRPCMessage> inboundSink;

	private final Sinks.Many<JSONRPCMessage> outboundSink;

	private ObjectMapper objectMapper;

	/** Scheduler for handling inbound messages */
	private Scheduler inboundScheduler;

	/** Scheduler for handling outbound messages */
	private Scheduler outboundScheduler;

	private volatile boolean isClosing = false;

	private final InputStream inputStream;

	private final OutputStream outputStream;

	private final Sinks.One<Void> inboundReady = Sinks.one();

	private final Sinks.One<Void> outboundReady = Sinks.one();

	/**
	 * Creates a new StdioServerTransport with a default ObjectMapper and System streams.
	 */
	public StdioServerTransport() {
		this(new ObjectMapper(), System.in, System.out);
	}

	/**
	 * Creates a new StdioServerTransport with the specified ObjectMapper and System
	 * streams.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 */
	public StdioServerTransport(ObjectMapper objectMapper) {
		this(objectMapper, System.in, System.out);
	}

	/**
	 * Creates a new StdioServerTransport with the specified ObjectMapper and streams.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 * @param inputStream The input stream to read from
	 * @param outputStream The output stream to write to
	 */
	public StdioServerTransport(ObjectMapper objectMapper, InputStream inputStream, OutputStream outputStream) {
		Assert.notNull(objectMapper, "The ObjectMapper can not be null");
		Assert.notNull(inputStream, "The InputStream can not be null");
		Assert.notNull(outputStream, "The OutputStream can not be null");

		this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
		this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();

		this.objectMapper = objectMapper;
		this.inputStream = inputStream;
		this.outputStream = outputStream;

		// Use bounded schedulers for better resource management
		this.inboundScheduler = Schedulers.newBoundedElastic(1, 1, "inbound");
		this.outboundScheduler = Schedulers.newBoundedElastic(1, 1, "outbound");
	}

	@Override
	public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
		return Mono.<Void>fromRunnable(() -> {
			handleIncomingMessages(handler);

			// Start threads
			startInboundProcessing();
			startOutboundProcessing();
		}).subscribeOn(Schedulers.boundedElastic());
	}

	private void handleIncomingMessages(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> inboundMessageHandler) {
		this.inboundSink.asFlux()
			.flatMap(message -> Mono.just(message)
				.transform(inboundMessageHandler)
				.contextWrite(ctx -> ctx.put("observation", "myObservation")))
			.subscribe();
	}

	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		return Mono.zip(inboundReady.asMono(), outboundReady.asMono()).then(Mono.defer(() -> {
			if (this.outboundSink.tryEmitNext(message).isSuccess()) {
				return Mono.empty();
			}
			else {
				return Mono.error(new RuntimeException("Failed to enqueue message"));
			}
		}));
	}

	/**
	 * Starts the inbound processing thread that reads JSON-RPC messages from stdin.
	 * Messages are deserialized and emitted to the inbound sink.
	 */
	private void startInboundProcessing() {
		this.inboundScheduler.schedule(() -> {
			inboundReady.tryEmitValue(null);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				String line;
				while (!isClosing && (line = reader.readLine()) != null) {
					try {
						JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.objectMapper, line);
						if (!this.inboundSink.tryEmitNext(message).isSuccess()) {
							if (!isClosing) {
								logger.error("Failed to enqueue message");
							}
							break;
						}
					}
					catch (Exception e) {
						if (!isClosing) {
							logger.error("Error processing inbound message", e);
						}
						break;
					}
				}
			}
			catch (IOException e) {
				if (!isClosing) {
					logger.error("Error reading from stdin", e);
				}
			}
			finally {
				if (!isClosing) {
					isClosing = true;
				}
				inboundSink.tryEmitComplete();
			}
		});
	}

	/**
	 * Starts the outbound processing thread that writes JSON-RPC messages to stdout.
	 * Messages are serialized to JSON and written with a newline delimiter.
	 */
	private void startOutboundProcessing() {
		Function<Flux<JSONRPCMessage>, Flux<JSONRPCMessage>> outboundConsumer = messages -> messages // @formatter:off
			.doOnSubscribe(subscription -> outboundReady.tryEmitValue(null))
			.publishOn(outboundScheduler)
			.handle((message, sink) -> {
				if (message != null && !isClosing) {
					try {
						String jsonMessage = objectMapper.writeValueAsString(message);
						// Escape any embedded newlines in the JSON message as per spec
						jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");

						synchronized (outputStream) {
							outputStream.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
							outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
							outputStream.flush();
						}
						sink.next(message);
					}
					catch (IOException e) {
						if (!isClosing) {
							logger.error("Error writing message", e);
							sink.error(new RuntimeException(e));
						}
					}
				}
				else if (isClosing) {
					sink.complete();
				}
			})
			.doOnComplete(() -> {
				if (!isClosing) {
					isClosing = true;
				}
				outboundSink.tryEmitComplete();
			})
			.doOnError(e -> {
				if (!isClosing) {
					logger.error("Error in outbound processing", e);
					isClosing = true;
					outboundSink.tryEmitComplete();
				}
			})
			.map(msg -> (JSONRPCMessage) msg);

			outboundConsumer.apply(outboundSink.asFlux()).subscribe();
	} // @formatter:on

	@Override
	public Mono<Void> closeGracefully() {
		return Mono.fromRunnable(() -> {
			isClosing = true;
			logger.debug("Initiating graceful shutdown");
			// }).then(Mono.delay(Duration.ofMillis(100))).then(Mono.fromRunnable(() -> {
		}).then(Mono.fromRunnable(() -> {
			try {
				// inboundSink.tryEmitComplete();
				// outboundSink.tryEmitComplete();

				inboundScheduler.dispose();
				outboundScheduler.dispose();

				// Wait for schedulers to terminate
				if (!inboundScheduler.isDisposed()) {
					inboundScheduler.disposeGracefully().block(Duration.ofSeconds(5));
				}
				if (!outboundScheduler.isDisposed()) {
					outboundScheduler.disposeGracefully().block(Duration.ofSeconds(5));
				}

				logger.info("Graceful shutdown completed");
			}
			catch (Exception e) {
				logger.error("Error during graceful shutdown", e);
			}
		})).then().subscribeOn(Schedulers.boundedElastic());
	}

	@Override
	public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
		return this.objectMapper.convertValue(data, typeRef);
	}

}
