/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.client.stdio;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.mcp.client.stdio.ServerParametersParser.McpServerConfigurations.McpServerConfiguration;

/**
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */

public class ServerParametersParser {

	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public record McpServerConfigurations(// @formatter:off
		@JsonProperty("mcpServers") Map<String, McpServerConfiguration> mcpServers) {

		@JsonInclude(JsonInclude.Include.NON_ABSENT)
		public record McpServerConfiguration(
			@JsonProperty("command") String command,
			@JsonProperty("args") List<String> args,
			@JsonProperty("env") Map<String, String> env) {
		}
	} // @formatter:on

	public static void main(String[] args) throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();

		// Assuming the JSON is in a file named "servers.json"
		String jsonInput = """
				{
				  "mcpServers": {
				    "filesystem": {
				      "command": "npx",
				      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/files"]
				    },
				    "git": {
				      "command": "uvx",
				      "args": ["mcp-server-git", "--repository", "path/to/git/repo"]
				    },
				    "github": {
				      "command": "npx",
				      "args": ["-y", "@modelcontextprotocol/server-github"],
				      "env": {
				        "GITHUB_PERSONAL_ACCESS_TOKEN": "<YOUR_TOKEN>"
				      }
				    },
				    "postgres": {
				      "command": "npx",
				      "args": ["-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost/mydb"]
				    }
				  }
				}
								""";

		// Deserialize JSON into McpServers
		McpServerConfigurations servers = objectMapper.readValue(jsonInput, McpServerConfigurations.class);

		// Access individual servers
		for (Map.Entry<String, McpServerConfiguration> entry : servers.mcpServers().entrySet()) {
			System.out.println("Key: " + entry.getKey());
			System.out.println("Command: " + entry.getValue().command());
			System.out.println("Arguments: " + String.join(", ", entry.getValue().args()));
			System.out.println("Environment Variables: " + entry.getValue().env());
			System.out.println("-----------");
		}
	}

}
