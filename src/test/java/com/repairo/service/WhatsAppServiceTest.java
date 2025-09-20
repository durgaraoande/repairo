package com.repairo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WhatsAppService whatsAppService;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(whatsAppService, "accessToken", "test_token");
        ReflectionTestUtils.setField(whatsAppService, "phoneNumberId", "test_phone_id");
        ReflectionTestUtils.setField(whatsAppService, "restTemplate", restTemplate);
        
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }

    @Test
    void testHandleIncomingMessage() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "Hi";
        
        // When
        whatsAppService.handleIncomingMessage(phoneNumber, messageText);

        // Then
        String output = outputStream.toString();
        assertTrue(output.contains("Received message from " + phoneNumber + ": " + messageText));
    }

    @Test
    void testHandleIncomingMessage_EmptyMessage() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "";
        
        // When
        whatsAppService.handleIncomingMessage(phoneNumber, messageText);

        // Then
        String output = outputStream.toString();
        assertTrue(output.contains("Received message from " + phoneNumber + ": " + messageText));
    }

    @Test
    void testHandleIncomingMessage_NullMessage() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = null;
        
        // When
        whatsAppService.handleIncomingMessage(phoneNumber, messageText);

        // Then
        String output = outputStream.toString();
        assertTrue(output.contains("Received message from " + phoneNumber + ": " + messageText));
    }

    @Test
    void testSendMessage_Success() {
        // Given
        String phoneNumber = "1234567890";
        String message = "Hello from service";
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // When
        whatsAppService.sendMessage(phoneNumber, message);

        // Then
        verify(restTemplate).postForEntity(
                eq("https://graph.facebook.com/v18.0/test_phone_id/messages"),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void testSendMessage_WithQuotes() {
        // Given
        String phoneNumber = "1234567890";
        String message = "Message with \"quotes\"";
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // When
        whatsAppService.sendMessage(phoneNumber, message);

        // Then
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void testSendMessage_Exception() {
        // Given
        String phoneNumber = "1234567890";
        String message = "Test message";
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        // When
        whatsAppService.sendMessage(phoneNumber, message);

        // Then
        String errorOutput = errorStream.toString();
        assertTrue(errorOutput.contains("Failed to send WhatsApp message"));
    }

    @Test
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
}
