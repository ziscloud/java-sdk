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
package spring.ai.experimental.mcp.spec;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import spring.ai.experimental.mcp.client.util.Assert;
import spring.ai.experimental.mcp.spec.McpSchema.JSONRPCMessage;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultMcpTransport implements McpAsyncTransport {

	protected final ObjectMapper objectMapper;

	private final Sinks.Many<String> errorSink;
	private final Sinks.Many<JSONRPCMessage> inboundSink;
	private final Sinks.Many<JSONRPCMessage> outboundSink;

	private Consumer<JSONRPCMessage> inboundMessageHandler = message -> System.out
			.println("Received message: " + message);
	private Consumer<String> errorHandler = error -> System.err.println("Received error: " + error);

	public DefaultMcpTransport() {		
		this(new ObjectMapper());
	}

	public DefaultMcpTransport(ObjectMapper objectMapper) {

		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.objectMapper = objectMapper;

		// TODO: consider the effects of buffering here -> the inter-process pipes are
		// independent and the notifications can flood the client/server.
		// Potentially, the interest in reading could be communicated from one party
		// to the other so the Blocking IO Threads can pause consuming the stream
		// buffers when there is no expectation for reading.

		this.errorSink = Sinks.many().unicast().onBackpressureBuffer();
		this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
		this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();

		this.handleIncomingMessages();
	}

	public ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	private void handleIncomingMessages() {
		this.inboundSink.asFlux()
				.subscribe(message -> this.inboundMessageHandler.accept(message));
	}

	private void handleIncomingErrors() {
		this.errorSink.asFlux().subscribe(e -> {
			this.errorHandler.accept(e);
			System.err.println(e);
		});
	}

	protected Sinks.Many<JSONRPCMessage> getInboundSink() {
		return inboundSink;
	}

	protected Sinks.Many<JSONRPCMessage> getOutboundSink() {
		return outboundSink;
	}

	protected Sinks.Many<String> getErrorSink() {
		return errorSink;
	}

	public void setInboudMessageHandler(Consumer<JSONRPCMessage> inboundMessageHandler) {
		this.inboundMessageHandler = inboundMessageHandler;
	}

	public void setErrorHandler(Consumer<String> errorHandler) {
		this.errorHandler = errorHandler;
	}

	// Start processing incoming messages
	public void start() {
		this.handleIncomingErrors();
		this.handleIncomingMessages();
	}

	// Close the transport
	@Override
	public void close() {
		// this.onClose();
	}

	@Override
	public Mono<Void> sendMessage(JSONRPCMessage message) {
		if (this.outboundSink.tryEmitNext(message).isSuccess()) {
			// TODO: essentially we could reschedule ourselves in some time and make
			// another attempt with the already read data but pause reading until
			// success
			// In this approach we delegate the retry and the backpressure onto the
			// caller. This might be enough for most cases.
			return Mono.empty();
		} else {
			return Mono.error(new RuntimeException("Failed to enqueue message"));
		}
	}
}
