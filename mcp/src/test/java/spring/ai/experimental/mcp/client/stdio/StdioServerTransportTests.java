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
package spring.ai.experimental.mcp.client.stdio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 *
 */
public class StdioServerTransportTests {

	@Mock
	private ServerParameters mockParams;

	@Mock
	private Process mockProcess;

	@Mock
	private BufferedReader mockErrorReader;

	@Mock
	private BufferedReader mockReader;

	@Mock
	private BufferedWriter mockWriter;

	@Mock
	ProcessBuilder mockProcessBuilder;

	private StdioServerTransport stdioServerTransport;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		when(mockParams.getCommand()).thenReturn("dummyCommand");
		when(mockParams.getArgs()).thenReturn(List.of("arg1", "arg2"));
		when(mockParams.getEnv()).thenReturn(new HashMap<>(Map.of("key", "value")));
		stdioServerTransport = new StdioServerTransport(mockParams, new ObjectMapper()) {
			@Override
			protected ProcessBuilder getProcessBuilder() {
				return mockProcessBuilder;
			}
		};
	}

	@Test
	public void testStart_Success() throws IOException {
		// Arrange
		when(mockProcessBuilder.start()).thenReturn(mockProcess);
		when(mockProcess.getInputStream()).thenReturn(mock(InputStream.class));
		when(mockProcess.getOutputStream()).thenReturn(mock(OutputStream.class));
		when(mockProcess.errorReader()).thenReturn(mockErrorReader);
		when(mockProcess.inputReader()).thenReturn(mockReader);
		when(mockProcess.outputWriter()).thenReturn(mockWriter);

		// Act
		stdioServerTransport.start();

		// Assert
		// TODO: assertThat(stdioServerTransport.isRunning).isTrue();
		verify(mockProcessBuilder).start();
	}

	@Test
	public void testStart_ProcessStartFailure() throws IOException {
		// Arrange
		when(mockProcessBuilder.start()).thenThrow(new IOException("Failed to start process"));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class, () -> stdioServerTransport.start());
		assertThat(exception.getMessage()).contains("Failed to start process with command");
	}

	@Test
	public void testStart_NullInputStream() throws IOException {
		// Arrange
		when(mockProcessBuilder.start()).thenReturn(mockProcess);
		when(mockProcess.getInputStream()).thenReturn(null);
		when(mockProcess.getOutputStream()).thenReturn(mock(OutputStream.class));

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class, () -> stdioServerTransport.start());
		assertThat(exception.getMessage()).contains("Process input or output stream is null");
		verify(mockProcess).destroy();
	}

	@Test
	public void testStart_NullOutputStream() throws IOException {
		// Arrange
		when(mockProcessBuilder.start()).thenReturn(mockProcess);
		when(mockProcess.getInputStream()).thenReturn(mock(InputStream.class));
		when(mockProcess.getOutputStream()).thenReturn(null);

		// Act & Assert
		RuntimeException exception = assertThrows(RuntimeException.class, () -> stdioServerTransport.start());
		assertThat(exception.getMessage()).contains("Process input or output stream is null");
		verify(mockProcess).destroy();
	}
}