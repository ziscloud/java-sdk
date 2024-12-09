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
package spring.ai.mcp.spec;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import spring.ai.mcp.spec.McpSchema.JSONRPCResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSessionTest {

	private McpTransport mockTransport;

	private ObjectMapper mockObjectMapper;

	private McpSession mcpSession;

	@BeforeEach
	void setUp() {
		mockTransport = mock(McpTransport.class);
		mockObjectMapper = mock(ObjectMapper.class);
		mcpSession = new McpSession(mockTransport, Duration.ofSeconds(5), mockObjectMapper);
	}

	@Test
	void constructorShouldThrowIfArgumentsAreNull() {
		assertThrows(IllegalArgumentException.class,
				() -> new McpSession(null, Duration.ofSeconds(5), mockObjectMapper));
		assertThrows(IllegalArgumentException.class, () -> new McpSession(mockTransport, null, mockObjectMapper));
		assertThrows(IllegalArgumentException.class, () -> new McpSession(mockTransport, Duration.ofSeconds(5), null));
	}

	@Test
	@Disabled
	void sendRequestShouldSendJsonRpcRequest() throws Exception {
		String method = "testMethod";
		Object params = Map.of("key", "value");
		TypeReference<String> typeRef = new TypeReference<>() {
		};

		CompletableFuture<McpSchema.JSONRPCResponse> mockResponseFuture = new CompletableFuture<>();
		mockResponseFuture.complete(new JSONRPCResponse("2.0", "1", "result", null));
		// when(mockTransport.sendMessage(any())).then(invocation -> null);
		when(mockObjectMapper.convertValue("result", typeRef)).thenReturn("result");

		CompletableFuture<String> result = mcpSession.sendRequest(method, params, typeRef);
		assertEquals("result", result.get());
	}

	@Test
	void sendRequestShouldHandleTimeout() {
		String method = "timeoutMethod";
		Object params = Map.of("key", "value");
		TypeReference<String> typeRef = new TypeReference<>() {
		};

		CompletableFuture<String> result = mcpSession.sendRequest(method, params, typeRef);
		assertThrows(Exception.class, result::get);
	}

	@Test
	void closeShouldInvokeTransportClose() {
		mcpSession.close();
		verify(mockTransport, times(1)).close();
	}

}
