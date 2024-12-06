package spring.ai.mcp.client;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;
import spring.ai.mcp.spec.McpSchema;
import spring.ai.mcp.spec.McpSession;
import spring.ai.mcp.spec.McpTransport;

public class McpAsyncSession {

	private final ConcurrentHashMap<Object, MonoSink<McpSchema.JSONRPCResponse>> pendingResponses
			= new ConcurrentHashMap<>();

	private final Duration requestTimeout;

	private final ObjectMapper objectMapper;

	private final McpTransport transport;

	public McpAsyncSession(Duration requestTimeout,
			ObjectMapper objectMapper,
			McpTransport transport) {
		this.requestTimeout = requestTimeout;
		this.objectMapper = objectMapper;
		this.transport = transport;

		this.transport.setMessageHandler(message -> {
			switch (message) {
				case McpSchema.JSONRPCResponse response -> {
					var sink = pendingResponses.remove(response.id());
					if (sink == null) {
						System.out.println("Unexpected response for unkown id " + response.id());
					} else {
						sink.success(response);
					}
				}
				case McpSchema.JSONRPCRequest request -> {
					System.out.println("Client does not yet support server requests");
				}
				case McpSchema.JSONRPCNotification notification -> {
					System.out.println("Notifications not yet supported");
				}
			}
		});

		this.transport.setErrorHandler(error -> System.out.println("Error received " + error));

		this.transport.start();
	}

	public <T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef) {
		String requestId = UUID.randomUUID().toString();

		return Mono.<McpSchema.JSONRPCResponse>create(sink -> {
			this.pendingResponses.put(requestId, sink);
			McpSchema.JSONRPCRequest jsonrpcRequest = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, method, requestId, requestParams);
			try {
				// TODO convert the transport to non-blocking as well
				this.transport.sendMessage(jsonrpcRequest);
			} catch (Exception e) {
				sink.error(e);
			}
		})
		           .timeout(this.requestTimeout)
		           .handle((jsonRpcResponse, s) -> {
					   if (jsonRpcResponse.error() != null) {
						   s.error(new McpError(jsonRpcResponse.error()));
					   } else {
						   s.next(this.objectMapper.convertValue(jsonRpcResponse.result(), typeRef));
					   }
		           });
	}

	public static class McpError extends RuntimeException {

		public McpError(Object error) {
			super(error.toString());
		}
	}

	public Mono<Void> sendNotification(String method) {
		return sendNotification(method, null);
	}

	public Mono<Void> sendNotification(String method, Map<String, Object> params) {
		McpSchema.JSONRPCNotification
				jsonrpcNotification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, method, params);
		try {
			// TODO: make it non-blocking
			this.transport.sendMessage(jsonrpcNotification);
		}
		catch (Exception e) {
			return Mono.error(new McpError(e));
		}
		return Mono.empty();
	}

	public Mono<Void> closeGracefully(Duration timeout) {
		// TODO handle the timeout in transport
		return Mono.fromRunnable(this.transport::close);
	}
}
