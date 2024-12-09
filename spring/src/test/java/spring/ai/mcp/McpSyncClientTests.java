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
package spring.ai.mcp;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import spring.ai.mcp.client.McpClient;
import spring.ai.mcp.client.McpSyncClient;
import spring.ai.mcp.client.stdio.StdioServerParameters;
import spring.ai.mcp.client.stdio.StdioServerTransport;
import spring.ai.mcp.spec.McpSchema.CallToolRequest;
import spring.ai.mcp.spec.McpSchema.CallToolResult;
import spring.ai.mcp.spec.McpSchema.ListResourcesResult;
import spring.ai.mcp.spec.McpSchema.ListToolsResult;
import spring.ai.mcp.spec.McpSchema.Tool;
import spring.ai.mcp.spec.McpSchema.Resource;

/**
 * Unit tests for MCP Client Session functionality.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
class McpSyncClientTests {

	private McpSyncClient mcpSyncClient;

	private StdioServerParameters stdioParams;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final String TEST_MESSAGE = "Hello MCP Spring AI!";

	@BeforeEach
	void setUp() {
		stdioParams = StdioServerParameters.builder("npx")
			.args("-y", "@modelcontextprotocol/server-everything", "dir")
			.build();

		assertThatCode(() -> {
			mcpSyncClient = McpClient.sync(new StdioServerTransport(stdioParams), TIMEOUT, new ObjectMapper());
			mcpSyncClient.initialize();
		}).doesNotThrowAnyException();
	}

	@AfterEach
	void tearDown() {
		if (mcpSyncClient != null) {
			assertThatCode(() -> mcpSyncClient.close()).doesNotThrowAnyException();
		}
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
		ListToolsResult tools = mcpSyncClient.listTools(null);

		assertThat(tools).isNotNull().satisfies(result -> {
			assertThat(result.tools()).isNotNull().isNotEmpty();

			Tool firstTool = result.tools().get(0);
			assertThat(firstTool.name()).isNotNull();
			assertThat(firstTool.description()).isNotNull();
		});
	}

	@Test
	@Timeout(15)
	void testPing() {
		assertThatCode(() -> mcpSyncClient.ping()).doesNotThrowAnyException();
	}

	@Test
	@Timeout(15)
	void testCallTool() {
		CallToolRequest callToolRequest = new CallToolRequest("echo", Map.of("message", TEST_MESSAGE));

		CallToolResult callToolResult = mcpSyncClient.callTool(callToolRequest);

		assertThat(callToolResult).isNotNull().satisfies(result -> {
			assertThat(result.content()).isNotNull();
			assertThat(result.isError()).isNull();
		});
	}

	@Test
	@Timeout(15)
	void testCallToolWithInvalidTool() {
		CallToolRequest invalidRequest = new CallToolRequest("nonexistent_tool", Map.of("message", TEST_MESSAGE));

		assertThatThrownBy(() -> mcpSyncClient.callTool(invalidRequest)).isInstanceOf(Exception.class);
	}

	@Test
	@Timeout(15)
	void testRootsListChanged() {
		assertThatCode(() -> mcpSyncClient.sendRootsListChanged()).doesNotThrowAnyException();
	}

	@Test
	@Timeout(15)
	void testListResources() {
		ListResourcesResult resources = mcpSyncClient.listResources(null);

		assertThat(resources).isNotNull().satisfies(result -> {
			assertThat(result.resources()).isNotNull();

			if (!result.resources().isEmpty()) {
				Resource firstResource = result.resources().get(0);
				assertThat(firstResource.uri()).isNotNull();
				assertThat(firstResource.name()).isNotNull();
			}
		});
	}

	@Test
	void testClientSessionState() {
		assertThat(mcpSyncClient).isNotNull();
	}

}
