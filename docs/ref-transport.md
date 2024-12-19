# MCP Transport Reference

## McpTransport Interface

`org.springframework.ai.mcp.spec.McpTransport`

The `McpTransport` interface defines the asynchronous transport layer for the Model Context Protocol (MCP). It provides the foundation for implementing custom transport mechanisms, handling bidirectional communication between client and server components using JSON-RPC format.

### Overview

The transport layer is designed to be protocol-agnostic, allowing for various implementations such as WebSocket, HTTP, or custom protocols. It manages the lifecycle of connections and handles message exchange between components.

### Key Responsibilities

- Managing the lifecycle of the transport connection
- Handling incoming messages and errors from the server
- Sending outbound messages to the server
- Message serialization and deserialization

### Methods

#### Connect
```java
Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler)
```

Initializes and starts the transport connection.

**Parameters:**
- `handler`: A function that processes incoming messages and returns response messages

**Returns:**
- A `Mono<Void>` that completes when the connection is established

#### Send Message
```java
Mono<Void> sendMessage(JSONRPCMessage message)
```

Sends a message to the server asynchronously.

**Parameters:**
- `message`: The JSON-RPC message to be sent to the server

**Returns:**
- A `Mono<Void>` that completes when the message has been sent

#### Close Gracefully
```java
Mono<Void> closeGracefully()
```

Closes the transport connection and releases any associated resources asynchronously.

**Returns:**
- A `Mono<Void>` that completes when the connection has been closed

#### Close
```java
default void close()
```

Synchronously closes the transport connection. By default, this method delegates to `closeGracefully()` and subscribes to the result.

#### Unmarshal From
```java
<T> T unmarshalFrom(Object data, TypeReference<T> typeRef)
```

Deserializes data into the specified type.

**Parameters:**
- `data`: The data to deserialize
- `typeRef`: The TypeReference describing the target type

**Returns:**
- The deserialized object of type T

### Implementation Notes

- The transport layer uses Project Reactor's `Mono` type for non-blocking operations
- Messages are exchanged using the JSON-RPC format as specified by the MCP protocol
- The interface is designed to be extensible, allowing for different transport implementations
- Current implementations include:
  - SSE (Server-Sent Events) Transport
  - STDIO Transport (Standard Input/Output)

### Usage Example

```java
McpTransport transport = // obtain transport instance

// Connect and handle messages
transport.connect(message -> {
    // Process incoming message
    return processMessage(message);
}).subscribe();

// Send a message
JSONRPCMessage message = new JSONRPCMessage();
// ... configure message
transport.sendMessage(message)
    .subscribe(
        null,
        error -> System.err.println("Error sending message: " + error),
        () -> System.out.println("Message sent successfully")
    );

// Close the transport
transport.closeGracefully()
    .subscribe(
        null,
        error -> System.err.println("Error closing transport: " + error),
        () -> System.out.println("Transport closed successfully")
    );
```

### Transport Implementations

#### SSE Transport
The SSE (Server-Sent Events) transport implementation provides a unidirectional communication channel from the server to the client using HTTP streaming.

#### STDIO Transport
The STDIO transport implementation uses standard input/output streams for communication, making it suitable for command-line applications and process-based communication.
