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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import spring.ai.mcp.spec.McpSchema.JSONRPCMessage;
import spring.ai.mcp.spec.McpSchema.JSONRPCNotification;
import spring.ai.mcp.spec.McpSchema.JSONRPCRequest;
import spring.ai.mcp.spec.McpSchema.JSONRPCResponse;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpSessionJSONRPCTest {

    private McpTransport mockTransport;
    private ObjectMapper objectMapper;
    private McpSession mcpSession;
    private ArgumentCaptor<Consumer<JSONRPCMessage>> messageHandlerCaptor;

    @BeforeEach
    void setUp() {
        mockTransport = mock(McpTransport.class);
        objectMapper = new ObjectMapper();
        mcpSession = new McpSession(mockTransport, Duration.ofSeconds(5), objectMapper);

        // Capture the message handler set during the session initialization
        messageHandlerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(mockTransport).setMessageHandler(messageHandlerCaptor.capture());
    }

    @Test
    void sendRequestShouldSendValidRequestMessage() {
        String method = "testMethod";
        Map<String, Object> params = Map.of("key", "value");

        // Call sendRequest
        CompletableFuture<String> future = mcpSession.sendRequest(method, params, new TypeReference<>() {});

        // Capture the JSONRPCRequest sent to the transport
        ArgumentCaptor<JSONRPCMessage> messageCaptor = ArgumentCaptor.forClass(JSONRPCMessage.class);
        verify(mockTransport).sendMessage(messageCaptor.capture());

        // Assert the message details
        JSONRPCRequest request = (JSONRPCRequest) messageCaptor.getValue();
        assertEquals(method, request.method());
        assertEquals("value", ((Map<?, ?>) request.params()).get("key"));
        assertNotNull(request.id());
    }

    @Test
    void sendNotificationShouldSendValidNotificationMessage() {
        String method = "notifyMethod";
        Map<String, Object> params = Map.of("param1", "value1");

        // Call sendNotification
        mcpSession.sendNotification(method, params);

        // Capture the JSONRPCNotification sent to the transport
        ArgumentCaptor<JSONRPCMessage> messageCaptor = ArgumentCaptor.forClass(JSONRPCMessage.class);
        verify(mockTransport).sendMessage(messageCaptor.capture());

        // Assert the notification details
        JSONRPCNotification notification = (JSONRPCNotification) messageCaptor.getValue();
        assertEquals(method, notification.method());
        assertEquals("value1", ((Map<?, ?>) notification.params()).get("param1"));
    }

    @Test
    void messageHandlerShouldProcessResponseCorrectly() {
        // Retrieve the captured message handler
        Consumer<JSONRPCMessage> messageHandler = messageHandlerCaptor.getValue();

        // Simulate a JSONRPCResponse
        JSONRPCResponse response = new JSONRPCResponse("2.0", "1", "resultValue", null);
        CompletableFuture<JSONRPCResponse> responseFuture = new CompletableFuture<>();
        mcpSession.waitingForResponseStreams.put("1", responseFuture);

        // Trigger the handler with the response
        messageHandler.accept(response);

        // Verify the response is completed
        assertTrue(responseFuture.isDone());
        assertEquals("resultValue", responseFuture.join().result());
    }

    @Test
    @Disabled
    void messageHandlerShouldInvokeHandleRequestForRequestMessage() {
        // Retrieve the captured message handler
        Consumer<JSONRPCMessage> messageHandler = messageHandlerCaptor.getValue();

        // Mock request data
        JSONRPCRequest request = new JSONRPCRequest("2.0", "testMethod", "1", Map.of("key", "value"));

        // Override the handleRequest to check invocation
        McpSession spySession = spy(mcpSession);
        doNothing().when(spySession).handleRequest(any());

        // Trigger the handler with a request
        messageHandler.accept(request);

        // Verify handleRequest was called
        verify(spySession).handleRequest(any());
    }

    @Test
    @Disabled
    void messageHandlerShouldInvokeHandleNotificationForNotificationMessage() {
        // Retrieve the captured message handler
        Consumer<JSONRPCMessage> messageHandler = messageHandlerCaptor.getValue();

        // Mock notification data
        JSONRPCNotification notification = new JSONRPCNotification("2.0", "notifyMethod", Map.of("key", "value"));

        // Override the handleNotification to check invocation
        McpSession spySession = spy(mcpSession);
        doNothing().when(spySession).handleNotification(any());

        // Trigger the handler with a notification
        messageHandler.accept(notification);

        // Verify handleNotification was called
        verify(spySession).handleNotification(notification);
    }

    @Test
    void closeShouldInvokeTransportClose() {
        // Call close on the session
        mcpSession.close();

        // Verify transport close was called
        verify(mockTransport).close();
    }
}
