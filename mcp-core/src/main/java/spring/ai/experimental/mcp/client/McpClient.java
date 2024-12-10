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
package spring.ai.experimental.mcp.client;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import spring.ai.experimental.mcp.spec.McpTransport;

/**
 * Factory class providing static methods for creating Model Context Protocol (MCP) clients.
 * This class serves as the main entry point for establishing connections with MCP servers,
 * offering both synchronous and asynchronous client implementations.
 * 
 * <p>The class provides factory methods to create either:
 * <ul>
 *   <li>{@link McpAsyncClient} for non-blocking operations
 *   <li>{@link McpSyncClient} for blocking operations
 * </ul>
 * 
 * <p>Each client type can be instantiated with default settings or with custom configuration
 * including request timeout and JSON object mapping.
 * 
 * <p>Future implementations will introduce a builder pattern for more flexible client configuration.
 * 
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public class McpClient {

    /**
     * Private constructor to prevent instantiation as this is a utility class
     * containing only static factory methods.
     */
    private McpClient() {
    }

    // TODO: introduce a builder like:
    //  McpClient.using(transport)
    //           .withRequestTimeout(Duration.ofSeconds(5))
    //           .withObjectMapper(objectMapper); <- or even a more sophisticated
    //                                               JSONtoPOJOCodec type
    //           .sync();

    /**
     * Creates an asynchronous MCP client with default configuration.
     *
     * @param transport The transport layer implementation for MCP communication
     * @return A new instance of {@link McpAsyncClient}
     */
    public static McpAsyncClient async(McpTransport transport) {
        return new McpAsyncClient(transport);
    }

    /**
     * Creates an asynchronous MCP client with custom configuration.
     *
     * @param transport The transport layer implementation for MCP communication
     * @param requestTimeout The duration to wait before timing out requests
     * @param objectMapper The Jackson object mapper for JSON serialization/deserialization
     * @return A new instance of {@link McpAsyncClient}
     */
    public static McpAsyncClient async(McpTransport transport, Duration requestTimeout,
            ObjectMapper objectMapper) {
        return new McpAsyncClient(transport, requestTimeout, objectMapper);
    }

    /**
     * Creates a synchronous MCP client with default configuration.
     * This method wraps an asynchronous client to provide synchronous operations.
     *
     * @param transport The transport layer implementation for MCP communication
     * @return A new instance of {@link McpSyncClient}
     */
    public static McpSyncClient sync(McpTransport transport) {
        return new McpSyncClient(async(transport));
    }

    /**
     * Creates a synchronous MCP client with custom configuration.
     * This method wraps an asynchronous client to provide synchronous operations.
     *
     * @param transport The transport layer implementation for MCP communication
     * @param requestTimeout The duration to wait before timing out requests
     * @param objectMapper The Jackson object mapper for JSON serialization/deserialization
     * @return A new instance of {@link McpSyncClient}
     */
    public static McpSyncClient sync(McpTransport transport, Duration requestTimeout,
            ObjectMapper objectMapper) {
        return new McpSyncClient(async(transport, requestTimeout, objectMapper));
    }
}
