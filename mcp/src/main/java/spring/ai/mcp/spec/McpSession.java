/*
* Copyright 2024 - 2024 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package spring.ai.mcp.spec;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import spring.ai.mcp.client.util.Assert;
import spring.ai.mcp.spec.McpSchema.JSONRPCNotification;
import spring.ai.mcp.spec.McpSchema.JSONRPCRequest;
import spring.ai.mcp.spec.McpSchema.JSONRPCResponse;

public class McpSession implements AutoCloseable {

	final ConcurrentHashMap<Object, CompletableFuture<JSONRPCResponse>> waitingForResponseStreams;

	private final Duration readTimeout;

	private final ObjectMapper objectMapper;

	private final McpTransport transport;

	public McpSession(McpTransport transport, Duration readTimeout, ObjectMapper objectMapper) {

		Assert.notNull(transport, "The transport can not be null");
		Assert.notNull(readTimeout, "The readTimeout can not be null");
		Assert.notNull(objectMapper, "The objectMapper can not be null");

		this.waitingForResponseStreams = new ConcurrentHashMap<>();

		this.readTimeout = readTimeout;
		this.objectMapper = objectMapper;

		this.transport = transport;

		this.transport.setMessageHandler(message -> {
			if (message instanceof JSONRPCResponse jsonRpcResponse) {
				CompletableFuture<JSONRPCResponse> responseFuture = this.waitingForResponseStreams
						.remove(jsonRpcResponse.id());
				if (responseFuture != null) {
					responseFuture.complete(jsonRpcResponse);
				}
			} else if (message instanceof JSONRPCRequest jsonRpcRequest) {
				handleRequest(new RequestResponder<>(jsonRpcRequest.id(), jsonRpcRequest));
			} else if (message instanceof JSONRPCNotification jsonRpcNotification) {
				handleNotification(jsonRpcNotification);
			}
		});

		this.transport.setErrorHandler(error -> {
			System.out.println("Received error: " + error);
		});

		this.transport.start();
	}

	public <T> CompletableFuture<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef) {

		String requestId = UUID.randomUUID().toString();
		CompletableFuture<JSONRPCResponse> responseFuture = new CompletableFuture<>();
		this.waitingForResponseStreams.put(requestId, responseFuture);

		JSONRPCRequest jsonrpcRequest = new JSONRPCRequest(McpSchema.JSONRPC_VERSION, method, requestId, requestParams);

		try {
			this.transport.sendMessage(jsonrpcRequest);
		} catch (Exception e) {
			return CompletableFuture.failedFuture(new McpError(e));
		}

		return responseFuture.orTimeout(this.readTimeout.toMillis(), TimeUnit.MILLISECONDS)
				.thenApply(jsonRpcResponse -> {
					if (jsonRpcResponse.error() != null) {
						throw new McpError(jsonRpcResponse.error());
					}

					return this.objectMapper.convertValue(jsonRpcResponse.result(), typeRef);
				});
	}

	public CompletableFuture<Void> sendNotification(String method) {
		return sendNotification(method, null);
	}

	public CompletableFuture<Void> sendNotification(String method, Map<String, Object> params) {
		JSONRPCNotification jsonrpcNotification = new JSONRPCNotification(McpSchema.JSONRPC_VERSION, method, params);
		try {
			this.transport.sendMessage(jsonrpcNotification);
		} catch (Exception e) {
			return CompletableFuture.failedFuture(new McpError(e));
		}
		return CompletableFuture.completedFuture(null);
	}

	protected void handleRequest(RequestResponder<JSONRPCRequest> responder) {
		// Override to handle requests
		System.out.println("Handling request: " + responder);
	}

	protected void handleNotification(JSONRPCNotification notification) {
		// Override to handle notifications
		System.out.println("Handling notification: " + notification);
	}

	@Override
	public void close() {
		this.transport.close();
	}

	public static class RequestResponder<R> {

		private final Object requestId;

		private final R request;

		public RequestResponder(Object requestId, R request) {
			this.requestId = requestId;
			this.request = request;
		}

		public CompletableFuture<Void> respond(Object response) {
			// Send response
			return CompletableFuture.completedFuture(null);
		}

	}

	public static class McpError extends RuntimeException {

		public McpError(Object error) {
			super(error.toString());
		}

	}

}
