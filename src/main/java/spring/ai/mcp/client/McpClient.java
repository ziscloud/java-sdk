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
package spring.ai.mcp.client;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import spring.ai.mcp.spec.McpSchema;
import spring.ai.mcp.spec.McpSession;
import spring.ai.mcp.spec.McpTransport;
import spring.ai.mcp.spec.McpSchema.CallToolRequest;
import spring.ai.mcp.spec.McpSchema.CallToolResult;
import spring.ai.mcp.spec.McpSchema.ClientCapabilities;
import spring.ai.mcp.spec.McpSchema.Implementation;
import spring.ai.mcp.spec.McpSchema.InitializeRequest;
import spring.ai.mcp.spec.McpSchema.InitializeResult;
import spring.ai.mcp.spec.McpSchema.ListResourceTemplatesResult;
import spring.ai.mcp.spec.McpSchema.ListResourcesResult;
import spring.ai.mcp.spec.McpSchema.ListToolsResult;
import spring.ai.mcp.spec.McpSchema.PaginatedRequest;
import spring.ai.mcp.spec.McpSchema.ReadResourceRequest;
import spring.ai.mcp.spec.McpSchema.Resource;
import spring.ai.mcp.spec.McpSchema.SubscribeRequest;
import spring.ai.mcp.spec.McpSchema.UnsubscribeRequest;
import spring.ai.mcp.spec.McpSchema.ClientCapabilities.RootCapabilities;

/**
 * The MCP client is the main entry point for interacting with the Model Context Protocol
 * (MCP) server.
 * 
 * 
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class McpClient extends McpSession {

	public McpClient(McpTransport transport) {
		this(transport, Duration.ofSeconds(10), new ObjectMapper());
	}

	public McpClient(McpTransport transport, Duration readTimeout, ObjectMapper objectMapper) {
		super(transport, readTimeout, objectMapper);
	}

	/**
	 * The initialization phase MUST be the first interaction between client and server.
	 * During this phase, the client and server:
	 * <ul>
	 * <li>Establish protocol version compatibility</li>
	 * <li>Exchange and negotiate capabilities</li>
	 * <li>Share implementation details</li>
	 * </ul>
	 * <br/>
	 * The client MUST initiate this phase by sending an initialize request containing:
	 * <ul>
	 * <li>The protocol version the client supports</li>
	 * <li>The client's capabilities</li>
	 * <li>Client implementation information</li>
	 * </ul>
	 *
	 * The server MUST respond with its own capabilities and information:
	 * {@link McpSchema.ServerCapabilities}. <br/>
	 * After successful initialization, the client MUST send an initialized notification
	 * to indicate it is ready to begin normal operations.
	 *
	 * <br/>
	 *
	 * <a href=
	 * "https://github.com/modelcontextprotocol/specification/blob/main/docs/specification/basic/lifecycle.md#initialization">Initialization
	 * Spec</a>
	 * @return the initialize result.
	 */
	public InitializeResult initialize() {

		InitializeRequest initializeRequest = new InitializeRequest(// @formatter:off
				McpSchema.LATEST_PROTOCOL_VERSION,
				new ClientCapabilities(null, new RootCapabilities(true), null),
				new Implementation("mcp-java-client", "0.0.1")); // @formatter:on

		CompletableFuture<InitializeResult> initializeResultFuture = this.sendRequest("initialize", initializeRequest,
				new TypeReference<InitializeResult>() {
				});

		try {
			InitializeResult initializeResult = initializeResultFuture.get();

			if (initializeRequest.protocolVersion() != McpSchema.LATEST_PROTOCOL_VERSION) {
				throw new McpError(
						"Unsupported protocol version from the server: " + initializeRequest.protocolVersion());
			}

			this.sendNotification("notifications/initialized", null);

			return initializeResult;
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	/**
	 * Send a roots/list_changed notification.
	 */
	public void sendRootsListChanged() {
		this.sendNotification("notifications/roots/list_changed");
	}

	/**
	 * Send a synchronous ping request.
	 */
	public void ping() {

		try {
			this.sendRequest("ping", null, new TypeReference<Void>() {
			}).get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	// --------------------------
	// Tools
	// --------------------------
	/**
	 * Send a tools/call request.
	 * @param callToolRequest the call tool request.
	 * @return the call tool result.
	 */
	public CallToolResult callTool(CallToolRequest callToolRequest) {
		try {
			return this.sendRequest("tools/call", callToolRequest, new TypeReference<CallToolResult>() {
			}).get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	/**
	 * Send a tools/list request.
	 * @return the list of tools result.
	 */
	public ListToolsResult listTools(String cursor) {
		try {
			return this.sendRequest("tools/list", new PaginatedRequest(cursor), new TypeReference<ListToolsResult>() {
			}).get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	// --------------------------
	// Resources
	// --------------------------

	/**
	 * Send a resources/list request.
	 * @param cursor the cursor
	 * @return the list of resources result.
	 */
	public ListResourcesResult listResources(String cursor) {
		try {
			return this
				.sendRequest("resources/list", new PaginatedRequest(cursor), new TypeReference<ListResourcesResult>() {
				})
				.get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	/**
	 * Send a resources/read request.
	 * @param resource the resource to read
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(Resource resource) {
		return this.readResource(new ReadResourceRequest(resource.uri()));
	}

	/**
	 * Send a resources/read request.
	 * @param readResourceRequest the read resource request.
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(ReadResourceRequest readResourceRequest) {
		try {
			return this
				.sendRequest("resources/read", readResourceRequest, new TypeReference<McpSchema.ReadResourceResult>() {
				})
				.get();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new McpError(e);
		}
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @param cursor the cursor
	 * @return the list of resource templates result.
	 */
	public ListResourceTemplatesResult listResourceTemplates(String cursor) {
		try {
			return this.sendRequest("resources/templates/list", new PaginatedRequest(cursor),
					new TypeReference<ListResourceTemplatesResult>() {
					})
				.get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	/**
	 * List Changed Notification. When the list of available resources changes, servers
	 * that declared the listChanged capability SHOULD send a notification:
	 */
	public void sendResourcesListChanged() {
		this.sendNotification("notifications/resources/list_changed");
	}

	/**
	 * Subscriptions. The protocol supports optional subscriptions to resource changes.
	 * Clients can subscribe to specific resources and receive notifications when they
	 * change.
	 *
	 * Send a resources/subscribe request.
	 * @param subscribeRequest the subscribe request contains the uri of the resource to
	 * subscribe to.
	 */
	public void subscribeResource(SubscribeRequest subscribeRequest) {
		try {
			this.sendRequest("resources/subscribe", subscribeRequest, new TypeReference<Void>() {
			}).get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}

	/**
	 * Send a resources/unsubscribe request.
	 * @param unsubscribeRequest the unsubscribe request contains the uri of the resource
	 * to unsubscribe from.
	 */
	public void unsubscribeResource(UnsubscribeRequest unsubscribeRequest) {
		try {
			this.sendRequest("resources/unsubscribe", unsubscribeRequest, new TypeReference<Void>() {
			}).get();
		}
		catch (Exception e) {
			throw new McpError(e);
		}
	}
}
