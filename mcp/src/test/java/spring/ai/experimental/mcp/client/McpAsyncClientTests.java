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
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import spring.ai.experimental.mcp.client.McpAsyncClient;
import spring.ai.experimental.mcp.client.McpClient;
import spring.ai.experimental.mcp.client.stdio.ServerParameters;
import spring.ai.experimental.mcp.client.stdio.StdioServerTransport;
import spring.ai.experimental.mcp.spec.McpSchema.CallToolRequest;
import spring.ai.experimental.mcp.spec.McpSchema.Resource;
import spring.ai.experimental.mcp.spec.McpSchema.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for MCP Client Session functionality.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
class McpAsyncClientTests {

	private McpAsyncClient mcpAsyncClient;

	private ServerParameters stdioParams;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final String TEST_MESSAGE = "Hello MCP Spring AI!";

	@BeforeEach
	void setUp() {
		stdioParams = ServerParameters.builder("npx")
			.args("-y", "@modelcontextprotocol/server-everything", "dir")
			.build();

		assertThatCode(() -> {
			mcpAsyncClient = McpClient.async(new StdioServerTransport(stdioParams), TIMEOUT, new ObjectMapper());
			mcpAsyncClient.initialize();
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		// if (mcpSyncClient != null) {
		// assertThatCode(() -> mcpSyncClient.close()).doesNotThrowAnyException();
		// }
	}

	@Test
	void testConstructorWithInvalidArguments() {
		assertThatThrownBy(() -> McpClient.sync(null, TIMEOUT, new ObjectMapper()))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> McpClient.sync(new StdioServerTransport(stdioParams), null, new ObjectMapper()))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> McpClient.sync(new StdioServerTransport(stdioParams), TIMEOUT, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Timeout(15) // Giving extra time beyond the client timeout
	void testListTools() {
		mcpAsyncClient.listTools(null).subscribe(result -> {
			assertThat(result.tools()).isNotNull().isNotEmpty();

			Tool firstTool = result.tools().get(0);
			assertThat(firstTool.name()).isNotNull();
			assertThat(firstTool.description()).isNotNull();
		});
	}

	@Test
	@Timeout(15)
	void testPing() {
		assertThatCode(() -> mcpAsyncClient.ping().block()).doesNotThrowAnyException();
	}

	@Test
	@Timeout(15)
	void testCallTool() {
		CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", TEST_MESSAGE));

		mcpAsyncClient.callTool(callToolRequest).subscribe(callToolResult -> {
			assertThat(callToolResult).isNotNull().satisfies(result -> {
				assertThat(result.content()).isNotNull();
				assertThat(result.isError()).isNull();
			});
		});
	}

	@Test
	@Timeout(15)
	void testCallToolWithInvalidTool() {
		CallToolRequest invalidRequest = new CallToolRequest("nonexistent_tool", Map.of("message", TEST_MESSAGE));

		assertThatThrownBy(() -> mcpAsyncClient.callTool(invalidRequest).block()).isInstanceOf(Exception.class);
	}

	@Test
	@Timeout(15)
	void testRootsListChanged() {
		assertThatCode(() -> mcpAsyncClient.sendRootsListChanged().block()).doesNotThrowAnyException();
	}

	@Test
	@Timeout(15)
	void testListResources() {
		mcpAsyncClient.listResources(null).subscribe(resources -> {
			assertThat(resources).isNotNull().satisfies(result -> {
				assertThat(result.resources()).isNotNull();

				if (!result.resources().isEmpty()) {
					Resource firstResource = result.resources().get(0);
					assertThat(firstResource.uri()).isNotNull();
					assertThat(firstResource.name()).isNotNull();
				}
			});
		});
	}

	@Test
	void testMcpAsyncClientState() {
		assertThat(mcpAsyncClient).isNotNull();
	}

}
