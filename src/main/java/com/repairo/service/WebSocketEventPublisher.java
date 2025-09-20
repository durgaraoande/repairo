package com.repairo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Publishes WebSocket events to STOMP topics for real-time admin updates.
 */
@Component
public class WebSocketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublisher.class);

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate; // optional when WS disabled

    private boolean available() { return messagingTemplate != null; }

    public void publishNewMessage(String customerId, String preview) {
        if (!available()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "NEW_MESSAGE");
        payload.put("customerId", customerId);
        payload.put("preview", preview);
        payload.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/admin/new-messages", payload);
        log.debug("Published NEW_MESSAGE event for {}", customerId);
    }

    public void publishStatusChange(String customerId, String oldStatus, String newStatus) {
        if (!available()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "STATUS_CHANGE");
        payload.put("customerId", customerId);
        payload.put("oldStatus", oldStatus);
        payload.put("newStatus", newStatus);
        payload.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/admin/status-updates", payload);
        log.debug("Published STATUS_CHANGE event for {} {}->{}", customerId, oldStatus, newStatus);
    }
}
