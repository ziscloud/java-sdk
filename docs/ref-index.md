# Spring AI MCP Reference Documentation

This documentation covers the Spring AI implementation of the Model Context Protocol (MCP) specification. The implementation follows the [official MCP specification](https://spec.modelcontextprotocol.io/specification/).

## Component Hierarchy

### 1. Transport Layer
[Transport](ref-transport.md)
- McpTransport Interface
- Transport Implementations (SSE, STDIO)
- Message Serialization/Deserialization

### 2. Session Layer
[Session Management](ref-session.md)
- McpSession Interface
- DefaultMcpSession Implementation
- Uses McpTransport for communication

### 3. High-Level Components
Both components operate at the same level, using McpSession internally:

[Client](ref-client.md)
- McpClient Factory
- Synchronous and Asynchronous Implementations
- Client Configuration and Builder Pattern

[Server](ref-server.md)
- McpServer Factory
- Synchronous and Asynchronous Implementations
- Server Configuration and Builder Pattern

### 4. Supporting Components

[Error Handling](ref-errors.md)
- McpError
- Error Codes and Handling
- Exception Hierarchy


## Component Relationships

```
┌─────────────┐    ┌─────────────┐
│  McpClient  │    │  McpServer  │
└─────────────┘    └─────────────┘
       │                  │
       └─────────┬────────┘
                 │
         ┌───────────────┐
         │   McpSession  │
         └───────────────┘
                 │
         ┌───────────────┐
         │  McpTransport │
         └───────────────┘
```

## Package Organization

The implementation is organized under the `org.springframework.ai.mcp` package with the following structure:

- `org.springframework.ai.mcp.spec` - Core interfaces and classes
  - McpTransport
  - McpSession
  - McpSchema
  - McpError
- `org.springframework.ai.mcp.client` - Client implementations
  - McpClient
  - McpAsyncClient
  - McpSyncClient
  - Client-specific transports
- `org.springframework.ai.mcp.server` - Server implementations
  - McpServer
  - McpAsyncServer
  - McpSyncServer
  - Server-specific transports
- `org.springframework.ai.mcp.util` - Utility classes
  - Assert
  - Utils

## Key Features

- Synchronous and Asynchronous implementations
- Multiple transport options (SSE, STDIO)
- Comprehensive error handling
- Builder pattern for configuration
- Reactive programming support with Project Reactor
- Type-safe message handling
- Resource and prompt management
- Tool discovery and execution
- Change notification system
