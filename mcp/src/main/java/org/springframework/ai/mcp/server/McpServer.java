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

package org.springframework.ai.mcp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.mcp.spec.McpSchema;
import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.ResourceTemplate;
import org.springframework.ai.mcp.spec.McpSchema.Tool;
import org.springframework.ai.mcp.spec.McpTransport;
import org.springframework.ai.mcp.util.Assert;

/**
 * Factory class providing static methods for creating Model Context Protocol (MCP)
 * servers. This class serves as the main entry point for establishing MCP servers,
 * offering both synchronous and asynchronous server implementations.
 *
 * <p>
 * The class provides factory methods to create either:
 * <ul>
 * <li>{@link McpAsyncServer} for non-blocking operations
 * <li>{@link McpSyncServer} for blocking operations
 * </ul>
 *
 * <p>
 * Use the builder pattern for flexible server configuration:
 *
 * <pre>{@code
 * McpServer.using(transport)
 * 		.serverInfo(new McpSchema.Implementation("my-server", "1.0.0"))
 * 		.tool(new MyToolHandler())
 * 		.async(); // or .sync()
 * }</pre>
 *
 * @author Christian Tzolov
 */
public interface McpServer {

	/**
	 * Registration of a tool with its handler function. MCP allows servers to expose
	 * tools that can be invoked by language models. Tools enable models to interact with
	 * external systems, such as querying databases, calling APIs, or performing
	 * computations. Each tool is uniquely identified by a name and includes metadata
	 * describing its schema.
	 *
	 * @param tool The tool definition
	 * @param call The function to handle tool execution
	 */
	public static record ToolRegistration(Tool tool, Function<Map<String, Object>, CallToolResult> call) {

	}

	public static record ResourceRegistration(McpSchema.Resource resource,
			Function<McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult> readHandler) {
	}

	public static record PromptRegistration(McpSchema.Prompt propmpt,
			Function<McpSchema.GetPromptRequest, McpSchema.GetPromptResult> promptHandler) {
	}

	/**
	 * Start building an MCP server with the specified transport.
	 * @param transport The transport layer implementation for MCP communication
	 * @return A new builder instance
	 */
	public static Builder using(McpTransport transport) {
		return new Builder(transport);
	}

	/**
	 * Builder class for creating MCP servers with custom configuration.
	 */
	public static class Builder {

		private final McpTransport transport;

		private McpSchema.Implementation serverInfo;

		private McpSchema.ServerCapabilities serverCapabilities;

		/**
		 * The Model Context Protocol (MCP) allows servers to expose tools that can be
		 * invoked by language models. Tools enable models to interact with external
		 * systems, such as querying databases, calling APIs, or performing computations.
		 * Each tool is uniquely identified by a name and includes metadata describing its
		 * schema.
		 */
		private final List<ToolRegistration> tools = new ArrayList<>();

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose resources to clients. Resources allow servers to share data that
		 * provides context to language models, such as files, database schemas, or
		 * application-specific information. Each resource is uniquely identified by a
		 * URI.
		 */
		private Map<String, ResourceRegistration> resources = new HashMap<>();

		private List<ResourceTemplate> resourceTemplates = new ArrayList<>();

		/**
		 * The Model Context Protocol (MCP) provides a standardized way for servers to
		 * expose prompt templates to clients. Prompts allow servers to provide structured
		 * messages and instructions for interacting with language models. Clients can
		 * discover available prompts, retrieve their contents, and provide arguments to
		 * customize them.
		 */
		private Map<String, PromptRegistration> prompts = new HashMap<>();

		private Builder(McpTransport transport) {
			Assert.notNull(transport, "Transport must not be null");
			this.transport = transport;
		}

		/**
		 * Set the server implementation information.
		 * @param serverInfo The server implementation details
		 * @return This builder instance
		 */
		public Builder info(McpSchema.Implementation serverInfo) {
			Assert.notNull(serverInfo, "Server info must not be null");
			this.serverInfo = serverInfo;
			return this;
		}

		/**
		 * Set the server implementation information using name and version.
		 * @param name The server name
		 * @param version The server version
		 * @return This builder instance
		 */
		public Builder info(String name, String version) {
			Assert.hasText(name, "Name must not be null or empty");
			Assert.hasText(version, "Version must not be null or empty");
			this.serverInfo = new McpSchema.Implementation(name, version);
			return this;
		}

		/**
		 * Set the server implementation information using name and version.
		 * @param name The server name
		 * @param version The server version
		 * @return This builder instance
		 */
		public Builder serverInfo(String name, String version) {
			Assert.notNull(name, "Server name must not be null");
			Assert.notNull(version, "Server version must not be null");
			this.serverInfo = new McpSchema.Implementation(name, version);
			return this;
		}

		/**
		 * Set the server capabilities.
		 * @param serverCapabilities The server capabilities configuration
		 * @return This builder instance
		 */
		public Builder capabilities(McpSchema.ServerCapabilities serverCapabilities) {
			this.serverCapabilities = serverCapabilities;
			return this;
		}

		/**
		 * Add a tool with its handler function.
		 * @param tool The tool definition
		 * @param handler The function to handle tool execution
		 * @return This builder instance
		 */
		public Builder tool(McpSchema.Tool tool, Function<Map<String, Object>, McpSchema.CallToolResult> handler) {
			Assert.notNull(tool, "Tool must not be null");
			Assert.notNull(handler, "Handler must not be null");

			this.tools.add(new ToolRegistration(tool, handler));

			return this;
		}

		/**
		 * Add multiple tool handlers to the server.
		 * @param toolRegistrations The list of tool handlers to add
		 * @return This builder instance
		 */
		public Builder tools(List<ToolRegistration> toolRegistrations) {
			Assert.notNull(toolRegistrations, "Tool handlers list must not be null");
			this.tools.addAll(toolRegistrations);
			return this;
		}

		/**
		 * Register tools with the server.
		 * @param toolRegistrations The map of tools to register
		 * @return This builder instance
		 */
		public Builder tools(ToolRegistration... toolRegistrations) {
			for (ToolRegistration tool : toolRegistrations) {
				this.tools.add(tool);
			}
			return this;
		}

		/**
		 * Register resources with the server.
		 * @param resourceRegsitrations The map of resources to register
		 * @return This builder instance
		 */
		public Builder resources(Map<String, ResourceRegistration> resourceRegsitrations) {
			Assert.notNull(resourceRegsitrations, "Resource handlers map must not be null");
			this.resources.putAll(resourceRegsitrations);
			return this;
		}

		/**
		 * Register resources with the server.
		 * @param resourceRegsitrations The list of resources to register
		 * @return This builder instance
		 */
		public Builder resources(List<ResourceRegistration> resourceRegsitrations) {
			Assert.notNull(resourceRegsitrations, "Resource handlers list must not be null");
			for (ResourceRegistration resource : resourceRegsitrations) {
				this.resources.put(resource.resource().name(), resource);
			}
			return this;
		}

		/**
		 * Register resources with the server.
		 * @param resourceRegistrations The list of resources to register
		 * @return This builder instance
		 */
		public Builder resources(ResourceRegistration... resourceRegistrations) {
			Assert.notNull(resourceRegistrations, "Resource handlers list must not be null");
			for (ResourceRegistration resource : resourceRegistrations) {
				this.resources.put(resource.resource().name(), resource);
			}
			return this;
		}

		/**
		 * Set the resource templates.
		 * @param resourceTemplates The list of resource templates
		 * @return This builder instance
		 */
		public Builder resourceTemplates(List<ResourceTemplate> resourceTemplates) {
			this.resourceTemplates = resourceTemplates;
			return this;
		}

		public Builder resourceTemplates(ResourceTemplate... resourceTemplates) {
			for (ResourceTemplate resourceTemplate : resourceTemplates) {
				this.resourceTemplates.add(resourceTemplate);
			}
			return this;
		}

		/**
		 * Register prompts with the server.
		 * @param prompts The map of prompts to register
		 * @return This builder instance
		 */
		public Builder prompts(Map<String, PromptRegistration> prompts) {
			this.prompts.putAll(prompts);
			return this;
		}

		/**
		 * Register prompts with the server.
		 * @param prompts The list of prompts to register
		 * @return This builder instance.
		 */
		public Builder prompts(List<PromptRegistration> prompts) {
			for (PromptRegistration prompt : prompts) {
				this.prompts.put(prompt.propmpt().name(), prompt);
			}
			return this;
		}

		/**
		 * Register prompts with the server.
		 * @param prompts The list of prompts to register
		 * @return This builder instance
		 */
		public Builder prompts(PromptRegistration... prompts) {
			for (PromptRegistration prompt : prompts) {
				this.prompts.put(prompt.propmpt().name(), prompt);
			}
			return this;
		}

		/**
		 * Build a synchronous MCP server.
		 * @return A new instance of {@link McpSyncServer}
		 */
		public McpSyncServer sync() {
			return new McpSyncServer(async());
		}

		/**
		 * Build an asynchronous MCP server.
		 * @return A new instance of {@link McpAsyncServer}
		 */
		public McpAsyncServer async() {
			if (serverInfo == null) {
				serverInfo = new McpSchema.Implementation("mcp-server", "1.0.0");
			}

			return new McpAsyncServer(transport, serverInfo, serverCapabilities, tools, resources, resourceTemplates,
					prompts);
		}

	}

}
