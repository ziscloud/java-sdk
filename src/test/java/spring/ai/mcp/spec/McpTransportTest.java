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


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import spring.ai.mcp.spec.McpSchema.JSONRPCMessage;
import spring.ai.mcp.spec.McpSchema.JSONRPCNotification;
import spring.ai.mcp.spec.McpSchema.JSONRPCRequest;
import spring.ai.mcp.spec.McpSchema.JSONRPCResponse;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpTransportTest {

    private McpTransport mockTransport;
    private McpSession mcpSession;

    @BeforeEach
    void setUp() {
        mockTransport = mock(McpTransport.class);
        mcpSession = new McpSession(mockTransport, Duration.ofSeconds(5), new ObjectMapper());
    }

    @Test
    void constructorShouldInitializeTransport() {
        // Verify transport setup during construction
        verify(mockTransport).setMessageHandler(any(Consumer.class));
        verify(mockTransport).setErrorHandler(any(Consumer.class));
        verify(mockTransport).start();
    }

    @Test
    void sendRequestShouldCallTransportSendMessage() {
        String method = "testMethod";
        Object params = Map.of("key", "value");

        // Call sendRequest
        mcpSession.sendRequest(method, params, new TypeReference<String>() {});

        // Capture the message sent to transport
        ArgumentCaptor<JSONRPCMessage> messageCaptor = ArgumentCaptor.forClass(JSONRPCMessage.class);
        verify(mockTransport).sendMessage(messageCaptor.capture());

        // Verify the message details
        JSONRPCRequest request = (JSONRPCRequest) messageCaptor.getValue();
        assertEquals("testMethod", request.method());
        assertEquals("value", ((Map<?, ?>) request.params()).get("key"));
        assertNotNull(request.id());
    }

    @Test
    void sendNotificationShouldCallTransportSendMessage() {
        String method = "notifyMethod";
        Map<String, Object> params = Map.of("param1", "value1");

        // Call sendNotification
        mcpSession.sendNotification(method, params);

        // Capture the notification sent to transport
        ArgumentCaptor<JSONRPCMessage> messageCaptor = ArgumentCaptor.forClass(JSONRPCMessage.class);
        verify(mockTransport).sendMessage(messageCaptor.capture());

        // Verify the notification details
        JSONRPCNotification notification = (JSONRPCNotification) messageCaptor.getValue();
        assertEquals("notifyMethod", notification.method());
        assertEquals("value1", ((Map<?, ?>) notification.params()).get("param1"));
    }

    @Test
    void messageHandlerShouldProcessResponse() {
        // Capture the message handler during initialization
        ArgumentCaptor<Consumer<JSONRPCMessage>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockTransport).setMessageHandler(handlerCaptor.capture());
        Consumer<JSONRPCMessage> handler = handlerCaptor.getValue();

        // Simulate a JSONRPCResponse
        JSONRPCResponse response = new JSONRPCResponse("2.0", "1", "result", null);
        handler.accept(response);

        // Verify the response was processed
        // (Use CompletableFuture to match the response if needed in real tests)
    }

    @Test
    void errorHandlerShouldLogErrors() {
        // Capture the error handler during initialization
        ArgumentCaptor<Consumer<String>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockTransport).setErrorHandler(handlerCaptor.capture());
        Consumer<String> errorHandler = handlerCaptor.getValue();

        // Simulate an error
        String errorMessage = "Test error";
        errorHandler.accept(errorMessage);

        // Verify the error was logged or processed
        // (In real tests, capture logging output or other side effects)
    }

    @Test
    void closeShouldStopTransport() {
        mcpSession.close();
        verify(mockTransport).close();
    }
}
