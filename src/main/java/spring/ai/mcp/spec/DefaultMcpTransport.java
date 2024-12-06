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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import spring.ai.mcp.spec.McpSchema.JSONRPCMessage;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultMcpTransport implements McpTransport {

	private final BlockingQueue<String> errorReadQueue;

	private final BlockingQueue<JSONRPCMessage> dataReadQueue;

	private final BlockingQueue<JSONRPCMessage> dataWriteQueue;

	private final ExecutorService executorService;

	private final Duration writeTimeout;

	private Consumer<JSONRPCMessage> messageReader = message -> System.out.println("Received message: " + message);

	private Consumer<String> errorReader = error -> System.err.println("Received error: " + error);

	public DefaultMcpTransport(Duration readTimeout) {

		this.errorReadQueue = new LinkedBlockingQueue<>();
		this.dataReadQueue = new LinkedBlockingQueue<>();
		this.dataWriteQueue = new LinkedBlockingQueue<>();
        this.writeTimeout = readTimeout;

		this.executorService = Executors.newFixedThreadPool(2);
		this.handleIncomingMessages();
	}

	private void handleIncomingMessages() {
		this.executorService.execute(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					JSONRPCMessage message = this.dataReadQueue.take();
					this.messageReader.accept(message);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void handleIncomingErrors() {
		this.executorService.execute(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					String error = this.errorReadQueue.take();
					this.errorReader.accept(error);
					System.err.println(error);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	protected BlockingQueue<JSONRPCMessage> getDataReadQueue() {
		return dataReadQueue;
	}

	protected BlockingQueue<JSONRPCMessage> getDataWriteQueue() {
		return dataWriteQueue;
	}

	protected BlockingQueue<String> getErrorReadQueue() {
		return errorReadQueue;
	}

	public void setMessageHandler(Consumer<JSONRPCMessage> messageReader) {
		this.messageReader = messageReader;
	}

	public void setErrorHandler(Consumer<String> errorReader) {
		this.errorReader = errorReader;
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
		this.executorService.shutdownNow();
	}

	public void sendMessage(JSONRPCMessage message) {
		// Use offer with timeout to prevent blocking indefinitely
		try {			
			if (!this.dataWriteQueue.offer(message, writeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new RuntimeException("Failed to enqueue message");
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
