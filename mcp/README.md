# Model Context Protocol (MCP) Java SDK

A Java implementation of the [Model Context Protocol](https://modelcontextprotocol.io/docs/concepts/architecture) specification, providing both synchronous and asynchronous clients for MCP server interactions.

## Overview

This SDK implements the Model Context Protocol, enabling seamless integration with AI models and tools through a standardized interface. It supports both synchronous and asynchronous communication patterns, making it suitable for various use cases and integration scenarios.

## Features

- Synchronous and Asynchronous client implementations
- Standard MCP operations support:
  - Tool discovery and execution
  - Resource management
  - Message creation
  - Server initialization
- Stdio-based server transport
- Reactive programming support using Project Reactor

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>spring.ai.experimental</groupId>
    <artifactId>mcp</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Async Client Example

```java
// Create server parameters
ServerParameters params = ServerParameters.builder("npx")
    .args("-y", "@modelcontextprotocol/server-everything", "dir")
    .build();

// Initialize the async client
Duration timeout = Duration.ofSeconds(10);
McpAsyncClient client = McpClient.async(
    new StdioServerTransport(params), 
    timeout,
    new ObjectMapper()
);

// Initialize the connection
client.initialize();

// List available tools
client.listTools(null).subscribe(result -> {
    List<Tool> tools = result.tools();
    // Process tools...
});

// Call a tool
CallToolRequest request = new CallToolRequest(
    "echo", 
    Map.of("message", "Hello MCP!")
);
client.callTool(request).subscribe(result -> {
    // Handle tool execution result...
});

// Cleanup
client.close();
```

### Sync Client Example

```java
// Create and initialize sync client
McpClient syncClient = McpClient.sync(
    new StdioServerTransport(params),
    timeout,
    new ObjectMapper()
);

// Initialize connection
syncClient.initialize();

// List tools synchronously
ListToolsResult tools = syncClient.listTools(null);

// Call tool synchronously
CallToolResult result = syncClient.callTool("echo", Map.of("message", "Hello!"));

// Cleanup
syncClient.close();
```

## Architecture

The SDK follows a layered architecture:

### Core Components

- **McpClient**: Main interface defining the synchronous operations
- **McpAsyncClient**: Async implementation using Project Reactor
- **McpSyncClient**: Synchronous wrapper around the async client
- **McpTransport**: Transport layer interface
- **DefaultMcpTransport**: Base transport implementation
- **StdioServerTransport**: Stdio-based server communication

### Key Interactions

1. Client initialization
   - Transport setup
   - Server connection establishment
   - Protocol handshake

2. Message Flow
   - Request creation
   - Transport layer handling
   - Response processing

3. Tool Execution
   - Tool discovery
   - Parameter validation
   - Execution handling
   - Result processing

## Error Handling

The SDK provides comprehensive error handling:

- Transport-level errors
- Protocol violations
- Tool execution failures
- Timeout handling
- Resource management errors

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Copyright 2024 - 2024 the original author or authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
