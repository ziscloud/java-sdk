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

package org.springframework.ai.mcp.server;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.mcp.server.transport.StdioServerTransport;
import org.springframework.ai.mcp.spec.McpTransport;

/**
 * Tests for {@link McpSyncServer} using {@link StdioServerTransport}.
 *
 * @author Christian Tzolov
 */
class StdioMcpSyncServerTests extends AbstractMcpSyncServerTests {

	private PipedInputStream testInput;

	private PipedOutputStream testOutput;

	private PipedOutputStream writeToInput;

	private PipedInputStream readFromOutput;

	@Override
	protected McpTransport createMcpTransport() {
		try {
			testInput = new PipedInputStream();
			writeToInput = new PipedOutputStream(testInput);
			testOutput = new PipedOutputStream();
			readFromOutput = new PipedInputStream(testOutput);
			return new StdioServerTransport(new ObjectMapper(), testInput, testOutput);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to initialize test streams", e);
		}
	}

	@Override
	protected void onStart() {
		// No special setup needed for stdio transport
	}

	@Override
	protected void onClose() {
		try {
			if (testInput != null) {
				testInput.close();
			}
			if (testOutput != null) {
				testOutput.close();
			}
			if (writeToInput != null) {
				writeToInput.close();
			}
			if (readFromOutput != null) {
				readFromOutput.close();
			}
		}
		catch (Exception e) {
			// Log but don't throw since this is cleanup
			e.printStackTrace();
		}
	}

}
