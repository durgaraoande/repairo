package com.repairo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsAppService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppService.class);

    @Value("${whatsapp.access.token}")
    private String accessToken;

    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    @Value("${whatsapp.api.version:v23.0}")
    private String apiVersion;

    @Value("${whatsapp.test.mode:true}")
    private boolean isTestMode;

    private final RestTemplate restTemplate = new RestTemplate();

    public void handleIncomingMessage(String phoneNumber, String messageText) {
        // This will be handled by MessageService in the controller
        System.out.println("Received message from " + phoneNumber + ": " + messageText);
    }

    public void sendMessage(String phoneNumber, String message) {
        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages";

            String payload = String.format("""
                {
                    "messaging_product": "whatsapp",
                    "to": "%s",
                    "type": "text",
                    "text": {
                        "body": "%s"
                    }
                }
                """, phoneNumber, message.replace("\"", "\\\""));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            
            logger.info("Sending WhatsApp message to {} in {} mode", phoneNumber, isTestMode ? "test" : "production");
            logger.debug("WhatsApp API URL: {}", url);
            logger.debug("WhatsApp payload: {}", payload);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            logger.info("WhatsApp message sent successfully. Response: {}", response.getBody());

        } catch (HttpClientErrorException e) {
            logger.error("Failed to send WhatsApp message to {}: {} {}", phoneNumber, e.getStatusCode(), e.getResponseBodyAsString());
            
            if (e.getStatusCode().value() == 401) {
                logger.error("WhatsApp API authentication failed. Please check:");
                logger.error("1. Access token is valid and not expired");
                logger.error("2. Access token has proper permissions (whatsapp_business_messaging)");
                logger.error("3. Phone number ID is correct");
                logger.error("Current access token (first 20 chars): {}...", accessToken != null ? accessToken.substring(0, Math.min(20, accessToken.length())) : "null");
                logger.error("Current phone number ID: {}", phoneNumberId);
            } else if (e.getStatusCode().value() == 400 && e.getResponseBodyAsString().contains("131030")) {
                if (isTestMode) {
                    logger.warn("Phone number {} is not in the test recipients list. Add it to your WhatsApp Business API test recipients to receive messages.", phoneNumber);
                } else {
                    logger.error("Phone number {} is not allowed to receive messages.", phoneNumber);
                }
            }
            
            // Re-throw or handle as needed - for now we'll log and continue
            throw new RuntimeException("WhatsApp message sending failed", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending WhatsApp message to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("WhatsApp message sending failed", e);
        }
    }
}
