package com.repairo.controller;

import com.repairo.service.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsAppWebhookController.class)
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WhatsAppService whatsAppService;

    @Test
    void testReceiveMessage() throws Exception {
        String payload = "{\"phone\":\"1234567890\",\"text\":\"Hello\"}";

        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk());

        Mockito.verify(whatsAppService).handleIncomingMessage(payload);
    }
}
