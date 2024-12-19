# MCP Session Reference

## McpSession Interface

`org.springframework.ai.mcp.spec.McpSession`

The MCP (Model Context Protocol) session components implement the [MCP specification](https://spec.modelcontextprotocol.io/) for managing bidirectional JSON-RPC communication between clients and model servers. 
The implementation provides a foundation for request-response patterns and notification handling, as well as managing the session lifecycle.

### Overview

The session operates asynchronously using Project Reactor's `Mono` type for non-blocking operations. It supports both request-response patterns and one-way notifications.

### Methods

#### Request-Response

```java
<T> Mono<T> sendRequest(String method, Object requestParams, TypeReference<T> typeRef)
```

Sends a request to the model server and expects a response of type T.

**Parameters:**
- `method`: The name of the method to be called on the server
- `requestParams`: The parameters to be sent with the request
- `typeRef`: The TypeReference describing the expected response type

**Returns:**
- A `Mono` that will emit the response when received

#### Notification

```java
Mono<Void> sendNotification(String method, Map<String, Object> params)
```

Sends a notification to the model server with parameters.

**Parameters:**
- `method`: The name of the notification method to be called on the server
- `params`: A map of parameters to be sent with the notification

**Returns:**
- A `Mono` that completes when the notification has been sent

#### Close Gracefully
```java
Mono<Void> closeGracefully()
```

Closes the session and releases any associated resources asynchronously.

**Returns:**
- A `Mono` that completes when the session has been closed

#### Close
```java
void close()
```

Closes the session and releases any associated resources synchronously.

### DefaultMcpSession Implementation

The `DefaultMcpSession` provides the concrete implementation of the MCP session with the following key features:

#### 1. Message Correlation
- Uses unique request IDs combining session UUID prefix and atomic counter
- Maintains thread-safe tracking of pending responses
- Implements timeout management for requests

#### 2. Handler Registration
```java
public interface RequestHandler {
    Mono<Object> handle(Object params);
}

public interface NotificationHandler {
    Mono<Void> handle(Object params);
}
```


### Usage Example

```java
McpSession session = // obtain session instance

// Send a request
TypeReference<ResponseType> typeRef = new TypeReference<ResponseType>() {};
Mono<ResponseType> response = session.sendRequest("method.name", requestParams, typeRef);

// Send a notification without parameters
Mono<Void> notificationResult = session.sendNotification("notification.method");

// Send a notification with parameters
Map<String, Object> params = new HashMap<>();
params.put("key", "value");
Mono<Void> paramNotificationResult = session.sendNotification("notification.method", params);

// Close the session gracefully
session.closeGracefully()
    .subscribe(
        null,
        error -> System.err.println("Error closing session: " + error),
        () -> System.out.println("Session closed successfully")
    );
```

### Implementation Notes

- The interface is designed to be transport-agnostic, allowing different transport implementations (SSE, STDIO) to be used.
- All operations except `close()` are non-blocking and return `Mono` instances.
- The `sendRequest` method uses Jackson's `TypeReference` for type-safe deserialization of responses.
- The default implementation of `sendNotification(String)` delegates to `sendNotification(String, Map)` with null parameters.
