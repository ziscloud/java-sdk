# MCP Client Reference

## McpClient Factory

`org.springframework.ai.mcp.client.McpClient`

The `McpClient` class serves as a factory for creating Model Context Protocol (MCP) clients. It provides the main entry point for establishing connections with MCP servers, offering both synchronous and asynchronous client implementations.

### Overview

The client factory uses a builder pattern for flexible configuration, allowing you to customize various aspects of the client including timeout durations, notification handlers, and more.

### Client Types

The factory can create two types of clients:
- `McpAsyncClient` for non-blocking operations
- `McpSyncClient` for blocking operations

### Builder Configuration

#### Basic Usage
```java
McpClient.using(transport)
    .requestTimeout(Duration.ofSeconds(5))
    .sync(); // or .async()
```

#### Builder Methods

##### using(McpTransport transport)
```java
public static Builder using(McpTransport transport)
```
Start building an MCP client with the specified transport.

##### requestTimeout(Duration requestTimeout)
```java
public Builder requestTimeout(Duration requestTimeout)
```
Set the request timeout duration (default is 20 seconds).

##### capabilities(ClientCapabilities capabilities)
```java
public Builder capabilities(ClientCapabilities capabilities)
```
Set the client capabilities that define what features the client supports.

##### clientInfo(Implementation clientInfo)
```java
public Builder clientInfo(Implementation clientInfo)
```
Set the client implementation information (name and version).

##### roots(List<Root> roots)
```java
public Builder roots(List<Root> roots)
```
Add a list of roots that define filesystem boundaries for server operations.

##### roots(Root... roots)
```java
public Builder roots(Root... roots)
```
Add roots that define filesystem boundaries for server operations.

##### toolsChangeConsumer(Consumer<List<Tool>> consumer)
```java
public Builder toolsChangeConsumer(Consumer<List<McpSchema.Tool>> toolsChangeConsumer)
```
Add a consumer for tool changes notifications.

##### resourcesChangeConsumer(Consumer<List<Resource>> consumer)
```java
public Builder resourcesChangeConsumer(Consumer<List<McpSchema.Resource>> resourcesChangeConsumer)
```
Add a consumer for resource changes notifications.

##### promptsChangeConsumer(Consumer<List<Prompt>> consumer)
```java
public Builder promptsChangeConsumer(Consumer<List<McpSchema.Prompt>> promptsChangeConsumer)
```
Add a consumer for prompt changes notifications.

##### sync()
```java
public McpSyncClient sync()
```
Build and return a synchronous MCP client.

##### async()
```java
public McpAsyncClient async()
```
Build and return an asynchronous MCP client.

### Usage Examples

#### Creating a Synchronous Client
```java
McpTransport transport = // obtain transport instance

// Configure client capabilities
ClientCapabilities capabilities = ClientCapabilities.builder()
    .experimental(Map.of("feature", "value"))
    .roots(true)
    .sampling()
    .build();

McpSyncClient client = McpClient.using(transport)
    .requestTimeout(Duration.ofSeconds(10))
    .rootsListChangedNotification(true)
    .toolsChangeConsumer(tools -> {
        System.out.println("Tools updated: " + tools);
    })
    .sync();

// Initialize client with capabilities
client.initialize(LATEST_PROTOCOL_VERSION, capabilities, 
    new Implementation("client-name", "1.0.0"));
```

#### Creating an Asynchronous Client
```java
McpTransport transport = // obtain transport instance
McpAsyncClient client = McpClient.using(transport)
    .requestTimeout(Duration.ofSeconds(5))
    .resourcesChangeConsumer(resources -> {
        System.out.println("Resources updated: " + resources);
    })
    .async();
```

#### Using Multiple Change Consumers
```java
McpClient.using(transport)
    .toolsChangeConsumer(tools -> {
        // Handle tool changes
    })
    .toolsChangeConsumer(tools -> {
        // Additional tool change handling
    })
    .resourcesChangeConsumer(resources -> {
        // Handle resource changes
    })
    .sync();
```

### Client Capabilities

The `ClientCapabilities` class represents features that clients can implement to enrich connected MCP servers. It uses a builder pattern for easy configuration.

#### ClientCapabilities Builder

```java
ClientCapabilities capabilities = ClientCapabilities.builder()
    .experimental(experimentalMap)
    .roots(true)
    .sampling()
    .build();
```

##### Builder Methods

###### experimental(Map<String, Object> experimental)
```java
public Builder experimental(Map<String, Object> experimental)
```
Set experimental features map for work-in-progress capabilities.

###### roots(Boolean listChanged)
```java
public Builder roots(Boolean listChanged)
```
Configure root capabilities with listChanged parameter. Roots define the boundaries of where servers can operate within the filesystem.

###### sampling()
```java
public Builder sampling()
```
Add sampling capabilities that allow servers to request LLM sampling from language models via clients.

### Client Operations

#### Root Management

Both synchronous and asynchronous clients provide methods for managing filesystem roots:

##### Synchronous API
```java
void addRoot(Root root)                    // Add a new root
void removeRoot(String rootUri)            // Remove a root by URI
void rootsListChangedNotification()        // Notify about roots list changes
```

##### Asynchronous API
```java
Mono<Void> addRoot(Root root)             // Add a new root
Mono<Void> removeRoot(String rootUri)      // Remove a root by URI
Mono<Void> rootsListChangedNotification()  // Notify about roots list changes
```

#### Tools API

Methods for interacting with server tools:

##### Synchronous API
```java
CallToolResult callTool(CallToolRequest request)     // Execute a tool
ListToolsResult listTools()                         // List all available tools
ListToolsResult listTools(String cursor)            // List tools with pagination
```

##### Asynchronous API
```java
Mono<CallToolResult> callTool(CallToolRequest request)
Mono<ListToolsResult> listTools()
Mono<ListToolsResult> listTools(String cursor)
```

#### Resources API

Methods for working with server resources:

##### Synchronous API
```java
ListResourcesResult listResources()                          // List all resources
ListResourcesResult listResources(String cursor)             // List resources with pagination
ReadResourceResult readResource(Resource resource)           // Read a resource
ReadResourceResult readResource(ReadResourceRequest request) // Read a resource by request
ListResourceTemplatesResult listResourceTemplates()          // List resource templates
void subscribeResource(SubscribeRequest request)            // Subscribe to resource updates
void unsubscribeResource(UnsubscribeRequest request)        // Unsubscribe from updates
void sendResourcesListChanged()                             // Notify about resource changes
```

##### Asynchronous API
```java
Mono<ListResourcesResult> listResources()
Mono<ListResourcesResult> listResources(String cursor)
Mono<ReadResourceResult> readResource(Resource resource)
Mono<ReadResourceResult> readResource(ReadResourceRequest request)
Mono<ListResourceTemplatesResult> listResourceTemplates()
Mono<Void> subscribeResource(SubscribeRequest request)
Mono<Void> unsubscribeResource(UnsubscribeRequest request)
Mono<Void> sendResourcesListChanged()
```

#### Prompts API

Methods for working with server prompts:

##### Synchronous API
```java
ListPromptsResult listPrompts()                        // List all prompts
ListPromptsResult listPrompts(String cursor)           // List prompts with pagination
GetPromptResult getPrompt(GetPromptRequest request)    // Get a specific prompt
void promptListChangedNotification()                   // Notify about prompt changes
```

##### Asynchronous API
```java
Mono<ListPromptsResult> listPrompts()
Mono<ListPromptsResult> listPrompts(String cursor)
Mono<GetPromptResult> getPrompt(GetPromptRequest request)
Mono<Void> promptListChangedNotification()
```

#### Lifecycle Management

Methods for managing client lifecycle:

##### Synchronous API
```java
InitializeResult initialize()     // Initialize client connection
void close()                     // Close client immediately
boolean closeGracefully()        // Close client with graceful shutdown
Object ping()                    // Send ping request
```

##### Asynchronous API
```java
Mono<InitializeResult> initialize()
void close()
Mono<Void> closeGracefully()
Mono<Object> ping()
```

### Implementation Notes

- The client factory uses a builder pattern for configuration
- Default request timeout is 20 seconds
- Multiple consumers can be registered for tools, resources, and prompts changes
- The builder validates that the transport is not null
- Deprecated static factory methods are available but using the builder pattern is recommended
- The synchronous client is implemented as a wrapper around the asynchronous client
- ClientCapabilities uses a builder pattern for configuring client features
- The synchronous client blocks on async operations with the configured request timeout
- Resource subscriptions require server support for the subscribe capability
- Root management requires client to be configured with root capabilities
