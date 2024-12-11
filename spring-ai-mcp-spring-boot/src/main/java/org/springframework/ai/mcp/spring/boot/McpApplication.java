package org.springframework.ai.mcp.spring.boot;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.mcp.client.McpClient;
import org.springframework.ai.mcp.client.McpSyncClient;
import org.springframework.ai.mcp.client.stdio.ServerParameters;
import org.springframework.ai.mcp.client.stdio.StdioServerTransport;
import org.springframework.ai.mcp.spring.McpFunctionCallback;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpApplication.class, args);
	}

	@Bean
	public CommandLineRunner test(ChatClient.Builder chatClientBuilder, List<McpFunctionCallback> functionCallbacks) {

		return args -> {

			var chatClient = chatClientBuilder.defaultFunctions(functionCallbacks.toArray(new McpFunctionCallback[0]))
					.build();

			System.out.println(
					"Can you connect to my SQLite database and tell me what products are available, and their prices?");

			var response = chatClient.prompt(
					"Can you connect to my SQLite database and tell me what products are available, and their prices?")
					.call().content();

			System.out.println(response);

			System.out.println("\nWhat's the average price of all products in the database?");

			response = chatClient.prompt(
					"What's the average price of all products in the database?")
					.call().content();

			System.out.println(response);

			System.out.println("\nCan you analyze the price distribution and suggest any pricing optimizations?");

			response = chatClient.prompt(
					"Can you analyze the price distribution and suggest any pricing optimizations?")
					.call().content();

			System.out.println(response);

			System.out.println("\nCould you help me design and create a new table for storing customer orders?");

			response = chatClient.prompt(
					"Could you help me design and create a new table for storing customer orders?")
					.call().content();

			System.out.println(response);

			// var response = chatClient.prompt("What is the content of the test.txt
			// file?").call().content();

			// System.out.println(response);

			// response = chatClient.prompt(
			// "Please summarize the content of the test.txt file, covert the result in
			// markdown format and store it into test.md")
			// .call()
			// .content();
			// System.out.println("Summary:" + response);
		};
	}

	@Bean
	public List<McpFunctionCallback> functionCallbacks(McpSyncClient mcpClient) {

		return mcpClient.listTools(null)
				.tools()
				.stream()
				.map(tool -> new McpFunctionCallback(mcpClient, tool))
				.toList();
	}

	@Bean(destroyMethod = "close")
	public McpSyncClient mcpClient() {

		// var stdioParams = ServerParameters.builder("npx")
		// .args("-y", "@modelcontextprotocol/server-filesystem",
		// "/Users/christiantzolov/Dev/projects/demo/mcp-server/dir")
		// .build();

		var stdioParams = ServerParameters.builder("uvx")
				.args("mcp-server-sqlite", "--db-path",
						"/Users/christiantzolov/test.db")
				.build();

		McpSyncClient mcpClient = null;
		try {
			mcpClient = McpClient.sync(new StdioServerTransport(stdioParams),
					Duration.ofSeconds(10), new ObjectMapper());

			var init = mcpClient.initialize();

			System.out.println("MCP Initialized: " + init);

			return mcpClient;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
