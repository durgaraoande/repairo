package com.repairo.integration;

import com.repairo.model.Customer;
import com.repairo.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/repairshop_test",
    "whatsapp.access.token=test_token",
    "whatsapp.phone.number.id=test_phone_id",
    "whatsapp.webhook.verify.token=test_verify_token",
    "app.encryption.key=test-encryption-key-16"
})
@ActiveProfiles("test")
class WebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void testWebhookFlow_NewCustomerOnboarding() throws Exception {
        // Given
        String payload = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "hi"}
                            }]
                        }
                    }]
                }]
            }
            """;

        // When
        mockMvc.perform(post("/webhook")
                .content(payload)
                .contentType("application/json"))
                .andExpect(status().isOk());

        // Then
        List<Customer> customers = customerRepository.findAll();
        assertEquals(1, customers.size());
        
        Customer customer = customers.get(0);
        assertNotNull(customer.getPhone());
        assertEquals(1, customer.getMessages().size());
        assertEquals("customer", customer.getMessages().get(0).getFrom());
    }

    @Test
    void testWebhookFlow_MultipleMessages() throws Exception {
        // First message
        String payload1 = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "hi"}
                            }]
                        }
                    }]
                }]
            }
            """;

        // Second message
        String payload2 = """
            {
                "entry": [{
                    "changes": [{
                        "value": {
                            "messages": [{
                                "from": "1234567890",
                                "text": {"body": "John Doe"}
                            }]
                        }
                    }]
                }]
            }
            """;

        // When
        mockMvc.perform(post("/webhook")
                .content(payload1)
                .contentType("application/json"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/webhook")
                .content(payload2)
                .contentType("application/json"))
                .andExpect(status().isOk());

        // Then
        List<Customer> customers = customerRepository.findAll();
        assertEquals(1, customers.size());
        
        Customer customer = customers.get(0);
        assertEquals(2, customer.getMessages().size());
        assertEquals("John Doe", customer.getName());
    }
}
