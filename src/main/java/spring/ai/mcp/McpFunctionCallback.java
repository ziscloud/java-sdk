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

import java.util.Map;

import spring.ai.mcp.client.McpClient;
import spring.ai.mcp.spec.McpSchema.CallToolRequest;
import spring.ai.mcp.spec.McpSchema.CallToolResult;
import spring.ai.mcp.spec.McpSchema.Tool;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class McpFunctionCallback implements FunctionCallback {

	private final McpClient mcpClient;

	private final Tool tool;

	public McpFunctionCallback(McpClient clientSession, Tool tool) {
		this.mcpClient = clientSession;
		this.tool = tool;
	}

	@Override
	public String getName() {
		return this.tool.name();
	}

	@Override
	public String getDescription() {
		return this.tool.description();
	}

	@Override
	public String getInputTypeSchema() {
		return ModelOptionsUtils.toJsonString(this.tool.inputSchema());
	}

	@Override
	public String call(String functionInput) {
		Map<String, Object> arguments = ModelOptionsUtils.jsonToMap(functionInput);
		CallToolResult response = this.mcpClient.callTool(new CallToolRequest(this.getName(), arguments));
		// Todo handle errors
		return ModelOptionsUtils.toJsonString(response.content());
	}

}
