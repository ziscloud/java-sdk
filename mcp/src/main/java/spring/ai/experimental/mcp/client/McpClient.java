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
 * The MCP client is the main entry point for interacting with the Model Context Protocol
 * (MCP) server.
 * 
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
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
