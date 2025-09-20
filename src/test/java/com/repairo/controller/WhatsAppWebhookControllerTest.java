package com.repairo.controller;

import com.repairo.service.WhatsAppService;
import com.repairo.service.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WhatsAppWebhookController.class)
@Import(WhatsAppWebhookControllerTest.TestSecurityConfig.class)
class WhatsAppWebhookControllerTest {

    @Configuration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @Test
    void testReceiveMessage() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "Hello"}
                            }]
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService).processIncomingMessage("1234567890", "Hello");
    }

    @Test
    void testVerifyWebhook() throws Exception {
        mockMvc.perform(get("/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.challenge", "test_challenge")
                .param("hub.verify_token", "repairo_webhook_token"))
                .andExpect(status().isOk())
                .andExpect(content().string("test_challenge"));
    }

    @Test
    void testVerifyWebhookWithInvalidToken() throws Exception {
        mockMvc.perform(get("/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.challenge", "test_challenge")
                .param("hub.verify_token", "invalid_token"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
    }

    @Test
    void testVerifyWebhookWithInvalidMode() throws Exception {
        mockMvc.perform(get("/webhook")
                .param("hub.mode", "invalid")
                .param("hub.challenge", "test_challenge")
                .param("hub.verify_token", "repairo_webhook_token"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Forbidden"));
    }

    @Test
    void testVerifyWebhookMissingParameters() throws Exception {
        mockMvc.perform(get("/webhook")
                .param("hub.mode", "subscribe")
                .param("hub.challenge", "test_challenge"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testReceiveMessageWithEmptyPayload() throws Exception {
        mockMvc.perform(post("/webhook")
                .content("")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService, Mockito.never()).processIncomingMessage(anyString(), anyString());
    }

    @Test
    void testReceiveMessageWithInvalidJson() throws Exception {
        String invalidPayload = "{\"phone\":\"1234567890\",\"text\":}";

        mockMvc.perform(post("/webhook")
                .content(invalidPayload)
                .contentType("application/json"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error"));
    }

    @Test
    void testReceiveMessageServiceThrowsException() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "Hello"}
                            }]
                        }
                    }]
                }]
            }
            """;
        
        Mockito.doThrow(new RuntimeException("Service error"))
                .when(messageService).processIncomingMessage(anyString(), anyString());

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error"));

        Mockito.verify(messageService).processIncomingMessage("1234567890", "Hello");
    }

    @Test
    void testReceiveMessageWithDifferentContentType() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "Hello"}
                            }]
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("text/plain"))
                .andExpect(status().isUnsupportedMediaType());

        Mockito.verify(messageService, Mockito.never()).processIncomingMessage(anyString(), anyString());
    }

    @Test
    void testReceiveMessageNoMessages() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": []
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService, Mockito.never()).processIncomingMessage(anyString(), anyString());
    }

    @Test
    void testReceiveMessageNoEntry() throws Exception {
        String payload = """
            {
                "entry": []
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService, Mockito.never()).processIncomingMessage(anyString(), anyString());
    }

    @Test
    void testReceiveMessageNoChanges() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": []
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService, Mockito.never()).processIncomingMessage(anyString(), anyString());
    }

    @Test
    void testReceiveMessageMultipleMessages() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [
                                {
                                    "from": "1234567890",
                                    "text": {"body": "First message"}
                                },
                                {
                                    "from": "0987654321",
                                    "text": {"body": "Second message"}
                                }
                            ]
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        // Only processes first message
        Mockito.verify(messageService).processIncomingMessage("1234567890", "First message");
    }

    @Test
    void testReceiveMessageMissingTextBody() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {}
                            }]
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService).processIncomingMessage("1234567890", "");
    }

    @Test
    void testReceiveMessageMissingFrom() throws Exception {
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "text": {"body": "Hello"}
                            }]
                        }
                    }]
                }]
            }
            """;

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        Mockito.verify(messageService).processIncomingMessage("", "Hello");
    }
}
