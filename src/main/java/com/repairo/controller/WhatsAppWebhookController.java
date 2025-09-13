package com.repairo.controller;

import com.repairo.service.MessageService;
import com.repairo.service.WhatsAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private WhatsAppService whatsAppService;
    
    @Value("${whatsapp.webhook.verify.token:repairo_webhook_token}")
    private String verifyToken;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam("hub.verify_token") String token) {
        
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        
        return ResponseEntity.status(403).body("Forbidden");
    }
    
    @PostMapping
    public ResponseEntity<String> receiveMessage(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode entry = root.path("entry");
            
            if (entry.isArray() && entry.size() > 0) {
                JsonNode changes = entry.get(0).path("changes");
                
                if (changes.isArray() && changes.size() > 0) {
                    JsonNode value = changes.get(0).path("value");
                    JsonNode messages = value.path("messages");
                    
                    if (messages.isArray() && messages.size() > 0) {
                        JsonNode message = messages.get(0);
                        String phoneNumber = message.path("from").asText();
                        String messageText = message.path("text").path("body").asText();
                        
                        // Process the message using WhatsAppService
                        whatsAppService.handleIncomingMessage(phoneNumber, messageText);
                    }
                }
            }
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            System.err.println("Error processing webhook: " + e.getMessage());
            return ResponseEntity.status(500).body("Error");
        }
    }
}
