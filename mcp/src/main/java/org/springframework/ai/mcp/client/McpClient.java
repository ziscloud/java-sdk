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

package org.springframework.ai.mcp.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.ClientCapabilities;
import org.springframework.ai.mcp.spec.McpSchema.CreateMessageRequest;
import org.springframework.ai.mcp.spec.McpSchema.CreateMessageResult;
import org.springframework.ai.mcp.spec.McpSchema.Implementation;
import org.springframework.ai.mcp.spec.McpSchema.Root;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.util.Assert;

/**
 * Factory class providing static methods for creating Model Context Protocol (MCP)
 * clients. This class serves as the main entry point for establishing connections with
 * MCP servers, offering both synchronous and asynchronous client implementations.
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link McpAsyncClient} for non-blocking operations
 * <li>{@link McpSyncClient} for blocking operations
 * </ul>
 *
 * <p>
 * Use the builder pattern for flexible client configuration:
 *
 * <pre>{@code
 * McpClient.using(transport)
 * 		.requestTimeout(Duration.ofSeconds(5))
 * 		.sync(); // or .async()
 * }</pre>
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public interface McpClient {

	/**
	 * Start building an MCP client with the specified transport.
	 * @param transport The transport layer implementation for MCP communication
	 * @return A new builder instance
	 */
	public static Builder using(McpTransport transport) {
		return new Builder(transport);
	}

	/**
	 * Builder class for creating MCP clients with custom configuration.
	 */
	public static class Builder {

		private final McpTransport transport;

		private Duration requestTimeout = Duration.ofSeconds(20); // Default timeout

		private ClientCapabilities capabilities;

		private Implementation clientInfo = new Implementation("Spring AI MCP Client", "0.3.0");

		private Map<String, Root> roots = new HashMap<>();

		private List<Consumer<List<McpSchema.Tool>>> toolsChangeConsumers = new ArrayList<>();

		private List<Consumer<List<McpSchema.Resource>>> resourcesChangeConsumers = new ArrayList<>();

		private List<Consumer<List<McpSchema.Prompt>>> promptsChangeConsumers = new ArrayList<>();

		private Function<CreateMessageRequest, CreateMessageResult> samplingHandler;

		private Builder(McpTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Set the request timeout duration.
		 * @param requestTimeout The duration to wait before timing out requests
		 * @return This builder instance
		 */
		public Builder requestTimeout(Duration requestTimeout) {
			Assert.notNull(requestTimeout, "Request timeout must not be null");
			this.requestTimeout = requestTimeout;
			return this;
		}

		public Builder capabilities(ClientCapabilities capabilities) {
			this.capabilities = capabilities;
			return this;
		}

		public Builder clientInfo(Implementation clientInfo) {
			this.clientInfo = clientInfo;
			return this;
		}

		public Builder roots(List<Root> roots) {
			for (Root root : roots) {
				this.roots.put(root.uri(), root);
			}
			return this;
		}

		public Builder roots(Root... roots) {
			for (Root root : roots) {
				this.roots.put(root.uri(), root);
			}
			return this;
		}

		public Builder sampling(Function<CreateMessageRequest, CreateMessageResult> samplingHandler) {
			this.samplingHandler = samplingHandler;
			return this;
		}

		public Builder toolsChangeConsumer(Consumer<List<McpSchema.Tool>> toolsChangeConsumer) {
			this.toolsChangeConsumers.add(toolsChangeConsumer);
			return this;
		}

		public Builder resourcesChangeConsumer(Consumer<List<McpSchema.Resource>> resourcesChangeConsumer) {
			this.resourcesChangeConsumers.add(resourcesChangeConsumer);
			return this;
		}

		public Builder promptsChangeConsumer(Consumer<List<McpSchema.Prompt>> promptsChangeConsumer) {
			this.promptsChangeConsumers.add(promptsChangeConsumer);
			return this;
		}

		/**
		 * Build a synchronous MCP client.
		 * @return A new instance of {@link McpSyncClient}
		 */
		public McpSyncClient sync() {
			return new McpSyncClient(async());
		}

		/**
		 * Build an asynchronous MCP client.
		 * @return A new instance of {@link McpAsyncClient}
		 */
		public McpAsyncClient async() {
			return new McpAsyncClient(transport, requestTimeout, clientInfo, capabilities, roots, toolsChangeConsumers,
					resourcesChangeConsumers, promptsChangeConsumers, samplingHandler);
		}

	}

}
