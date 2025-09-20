package com.repairo.controller;

import com.repairo.service.MessageService;
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

    @Value("${whatsapp.webhook.verify.token}")
    private String verifyToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode") String mode,
            @RequestParam(value = "hub.challenge") String challenge,
            @RequestParam(value = "hub.verify_token") String token) {

        System.out.println("Webhook verify: mode=" + mode + ", challenge=" + challenge + ", token=" + token + ", expectedToken=" + verifyToken);

        if (mode == null || challenge == null || token == null) {
            return ResponseEntity.badRequest().body("Missing parameters");
        }
        if ("subscribe".equalsIgnoreCase(mode.trim()) && verifyToken.equals(token.trim())) {
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
                        
                        // Process the message using MessageService
                        messageService.processIncomingMessage(phoneNumber, messageText);
                    }
                }
            }
            
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error");
        }
    }
}
