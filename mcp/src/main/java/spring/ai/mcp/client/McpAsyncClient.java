package spring.ai.mcp.client;

import java.time.Duration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import spring.ai.mcp.spec.McpAsyncTransport;
import spring.ai.mcp.spec.McpSchema;

public class McpAsyncClient extends McpAsyncSession {

	public McpAsyncClient(McpAsyncTransport transport) {
		this(transport, Duration.ofSeconds(10), new ObjectMapper());
	}

	public McpAsyncClient(McpAsyncTransport transport, Duration requestTimeout, ObjectMapper objectMapper) {		
		super(requestTimeout, objectMapper, transport);
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
	public Mono<McpSchema.InitializeResult> initialize() {
		McpSchema.InitializeRequest initializeRequest = new McpSchema.InitializeRequest(// @formatter:off
				McpSchema.LATEST_PROTOCOL_VERSION,
				new McpSchema.ClientCapabilities(null, new McpSchema.ClientCapabilities.RootCapabilities(true), null),
				new McpSchema.Implementation("mcp-java-client", "0.0.1")); // @formatter:on

		Mono<McpSchema.InitializeResult> result =
				this.sendRequest("initialize", initializeRequest, new TypeReference<McpSchema.InitializeResult>() {});

		return result.flatMap(initializeResult -> {
			if (!McpSchema.LATEST_PROTOCOL_VERSION.equals(initializeResult.protocolVersion())) {
				return Mono.error(
						new McpError("Unsupported protocol version from the server: "
								+ initializeResult.protocolVersion()));
			} else {
				return this.sendNotification("notifications/initialized", null)
				           .thenReturn(initializeResult);
			}
		});
	}

	/**
	 * Send a roots/list_changed notification.
	 */
	public Mono<Void> sendRootsListChanged() {
		return this.sendNotification("notifications/roots/list_changed");
	}

	/**
	 * Send a synchronous ping request.
	 */
	public Mono<Void> ping() {
		return this.sendRequest("ping", null,
				new TypeReference<Void>() {});
	}

	// --------------------------
	// Tools
	// --------------------------
	/**
	 * Send a tools/call request.
	 * @param callToolRequest the call tool request.
	 * @return the call tool result.
	 */
	public Mono<McpSchema.CallToolResult> callTool(McpSchema.CallToolRequest callToolRequest) {
		return this.sendRequest("tools/call", callToolRequest,
				new TypeReference<McpSchema.CallToolResult>() {});
	}

	/**
	 * Send a tools/list request.
	 * @return the list of tools result.
	 */
	public Mono<McpSchema.ListToolsResult> listTools(String cursor) {
		return this.sendRequest("tools/list", new McpSchema.PaginatedRequest(cursor),
				new TypeReference<McpSchema.ListToolsResult>() {});
	}

	// --------------------------
	// Resources
	// --------------------------

	/**
	 * Send a resources/list request.
	 * @param cursor the cursor
	 * @return the list of resources result.
	 */
	public Mono<McpSchema.ListResourcesResult> listResources(String cursor) {
		return this.sendRequest("resources/list", new McpSchema.PaginatedRequest(cursor),
				new TypeReference<McpSchema.ListResourcesResult>() {});
	}

	/**
	 * Send a resources/read request.
	 * @param resource the resource to read
	 * @return the resource content.
	 */
	public Mono<McpSchema.ReadResourceResult> readResource(McpSchema.Resource resource) {
		return this.readResource(new McpSchema.ReadResourceRequest(resource.uri()));
	}

	/**
	 * Send a resources/read request.
	 * @param readResourceRequest the read resource request.
	 * @return the resource content.
	 */
	public Mono<McpSchema.ReadResourceResult> readResource(McpSchema.ReadResourceRequest readResourceRequest) {
		return this.sendRequest("resources/read", readResourceRequest,
				new TypeReference<McpSchema.ReadResourceResult>() {});
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @param cursor the cursor
	 * @return the list of resource templates result.
	 */
	public Mono<McpSchema.ListResourceTemplatesResult> listResourceTemplates(String cursor) {
		return this.sendRequest("resources/templates/list", new McpSchema.PaginatedRequest(cursor),
				new TypeReference<McpSchema.ListResourceTemplatesResult>() {});
	}

	/**
	 * List Changed Notification. When the list of available resources changes, servers
	 * that declared the listChanged capability SHOULD send a notification:
	 */
	public Mono<Void> sendResourcesListChanged() {
		return this.sendNotification("notifications/resources/list_changed");
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
	public Mono<Void> subscribeResource(McpSchema.SubscribeRequest subscribeRequest) {
		return this.sendRequest("resources/subscribe", subscribeRequest,
				new TypeReference<Void>() {});
	}

	/**
	 * Send a resources/unsubscribe request.
	 * @param unsubscribeRequest the unsubscribe request contains the uri of the resource
	 * to unsubscribe from.
	 */
	public Mono<Void> unsubscribeResource(McpSchema.UnsubscribeRequest unsubscribeRequest) {
		return this.sendRequest("resources/unsubscribe", unsubscribeRequest,
				new TypeReference<Void>() {});
	}
}
