package spring.ai.experimental.mcp.client;

import java.time.Duration;

import spring.ai.experimental.mcp.client.util.Assert;
import spring.ai.experimental.mcp.spec.McpSchema;

public class McpSyncClient implements AutoCloseable {

	// TODO: Consider providing a client config to set this properly
	//  this is currently a concern only because AutoCloseable is used - perhaps it is
	//  not a requirement?
	private static final long DEFAULT_CLOSE_TIMEOUT_MS = 10_000L;

	McpAsyncClient delegate;

	public McpSyncClient(McpAsyncClient delegate) {
		Assert.notNull(delegate, "The delegate can not be null");
		this.delegate = delegate;
	}

	@Override
	public void close() {
		delegate.closeGracefully(Duration.ofMillis(DEFAULT_CLOSE_TIMEOUT_MS)).block();
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
	public McpSchema.InitializeResult initialize() {
		// TODO: block takes no argument here as we assume the async client is
		//  configured with a requestTimeout at all times
		return delegate.initialize()
		               .block();
	}

	/**
	 * Send a roots/list_changed notification.
	 */
	public void sendRootsListChanged() {
		delegate.sendRootsListChanged()
		        .block();
	}

	/**
	 * Send a synchronous ping request.
	 */
	public void ping() {
		delegate.ping()
		        .block();
	}

	// --------------------------
	// Tools
	// --------------------------
	/**
	 * Send a tools/call request.
	 * @param callToolRequest the call tool request.
	 * @return the call tool result.
	 */
	public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest callToolRequest) {
		return delegate.callTool(callToolRequest)
		               .block();
	}

	/**
	 * Send a tools/list request.
	 * @return the list of tools result.
	 */
	public McpSchema.ListToolsResult listTools(String cursor) {
		return delegate.listTools(cursor)
				.block();
	}

	// --------------------------
	// Resources
	// --------------------------

	/**
	 * Send a resources/list request.
	 * @param cursor the cursor
	 * @return the list of resources result.
	 */
	public McpSchema.ListResourcesResult listResources(String cursor) {
		return delegate.listResources(cursor)
				.block();
	}

	/**
	 * Send a resources/read request.
	 * @param resource the resource to read
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(McpSchema.Resource resource) {
		return delegate.readResource(resource)
				.block();
	}

	/**
	 * Send a resources/read request.
	 * @param readResourceRequest the read resource request.
	 * @return the resource content.
	 */
	public McpSchema.ReadResourceResult readResource(McpSchema.ReadResourceRequest readResourceRequest) {
		return delegate.readResource(readResourceRequest)
				.block();
	}

	/**
	 * Resource templates allow servers to expose parameterized resources using URI
	 * templates. Arguments may be auto-completed through the completion API.
	 *
	 * Request a list of resource templates the server has.
	 * @param cursor the cursor
	 * @return the list of resource templates result.
	 */
	public McpSchema.ListResourceTemplatesResult listResourceTemplates(String cursor) {
		return delegate.listResourceTemplates(cursor)
				.block();
	}

	/**
	 * List Changed Notification. When the list of available resources changes, servers
	 * that declared the listChanged capability SHOULD send a notification:
	 */
	public void sendResourcesListChanged() {
		delegate.sendResourcesListChanged()
				.block();
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
	public void subscribeResource(McpSchema.SubscribeRequest subscribeRequest) {
		delegate.subscribeResource(subscribeRequest)
				.block();
	}

	/**
	 * Send a resources/unsubscribe request.
	 * @param unsubscribeRequest the unsubscribe request contains the uri of the resource
	 * to unsubscribe from.
	 */
	public void unsubscribeResource(McpSchema.UnsubscribeRequest unsubscribeRequest) {
		delegate.unsubscribeResource(unsubscribeRequest)
				.block();
	}
}
