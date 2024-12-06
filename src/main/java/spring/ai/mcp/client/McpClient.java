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
public class McpClient {

	private McpClient() {
	}

	// TODO: introduce a builder like:
	//  McpClient.using(transport)
	//           .withRequestTimeout(Duration.ofSeconds(5))
	//           .withObjectMapper(objectMapper); <- or even a more sophisticated
	//                                               JSONtoPOJOCodec type
	//           .sync();

	public static McpAsyncClient async(McpTransport transport) {
		return new McpAsyncClient(transport);
	}

	public static McpAsyncClient async(McpTransport transport, Duration requestTimeout,
			ObjectMapper objectMapper) {
		return new McpAsyncClient(transport, requestTimeout, objectMapper);
	}

	public static McpSyncClient sync(McpTransport transport) {
		return new McpSyncClient(async(transport));
	}

	public static McpSyncClient sync(McpTransport transport, Duration requestTimeout,
			ObjectMapper objectMapper) {
		return new McpSyncClient(async(transport, requestTimeout, objectMapper));
	}


}
