# MCP Error Handling Reference

## McpError Class

`org.springframework.ai.mcp.spec.McpError`

The `McpError` class is a runtime exception that represents errors that occur during Model Context Protocol (MCP) operations. It extends `RuntimeException` and provides a way to encapsulate error information from the MCP protocol.

### Overview

The McpError class is designed to handle various error scenarios that can occur during MCP communication, including protocol errors, transport errors, and application-specific errors.

### Class Definition

```java
public class McpError extends RuntimeException {
    public McpError(Object error) {
        super(error.toString());
    }
}
```

### Common Error Scenarios

1. **Transport Errors**
   - Connection failures
   - Timeout errors
   - Protocol violations

2. **Protocol Errors**
   - Invalid JSON-RPC messages
   - Missing required fields
   - Invalid method names

3. **Application Errors**
   - Tool execution failures
   - Resource access errors
   - Invalid parameters

### Error Handling Best Practices

#### Client-Side Error Handling

```java
McpClient client = McpClient.using(transport)
    .async();

client.sendRequest("method", params)
    .onErrorResume(McpError.class, error -> {
        // Handle MCP-specific errors
        logger.error("MCP error occurred: {}", error.getMessage());
        return Mono.empty();
    })
    .onErrorResume(Exception.class, error -> {
        // Handle other exceptions
        logger.error("Unexpected error: {}", error.getMessage());
        return Mono.empty();
    });
```

#### Server-Side Error Handling

```java
McpServer.using(transport)
    .tool(new McpSchema.Tool("example", "Example tool", inputSchema),
        args -> {
            try {
                // Tool implementation
                return new McpSchema.CallToolResult(result);
            }
            catch (Exception e) {
                // Convert to MCP error
                throw new McpError("Tool execution failed: " + e.getMessage());
            }
        })
    .async();
```

### Implementation Notes

- McpError is a runtime exception, so it doesn't require explicit declaration in method signatures
- The error message is created by calling toString() on the provided error object
- Error handling should be implemented at both client and server sides
- Reactive error handling using Project Reactor's error operators is recommended
- Consider logging errors for debugging and monitoring purposes

### Error Recovery Strategies

1. **Retry Logic**
```java
client.sendRequest("method", params)
    .retry(3) // Retry up to 3 times
    .onErrorResume(McpError.class, error -> {
        // Handle failure after retries
        return Mono.empty();
    });
```

2. **Fallback Values**
```java
client.sendRequest("method", params)
    .onErrorReturn(McpError.class, fallbackValue);
```

3. **Circuit Breaking**
```java
// Using Resilience4j or similar library
CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("mcpClient");
client.sendRequest("method", params)
    .transform(CircuitBreakerOperator.of(circuitBreaker));
```

### Error Logging and Monitoring

```java
client.sendRequest("method", params)
    .doOnError(McpError.class, error -> {
        // Log error details
        logger.error("MCP error: {}", error.getMessage());
        // Update metrics
        errorCounter.increment();
    });
```

### Integration with Spring Error Handling

```java
@ControllerAdvice
public class McpErrorHandler {
    @ExceptionHandler(McpError.class)
    public ResponseEntity<ErrorResponse> handleMcpError(McpError error) {
        ErrorResponse response = new ErrorResponse(
            "MCP_ERROR",
            error.getMessage()
        );
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
```

### Testing Error Scenarios

```java
@Test
void testMcpError() {
    McpError error = new McpError("Test error");
    assertEquals("Test error", error.getMessage());
    
    assertThrows(McpError.class, () -> {
        throw new McpError("Test error");
    });
}
