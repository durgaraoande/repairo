package com.repairo.model;

import org.springframework.data.annotation.Id;
import java.time.LocalDateTime;

public class Message {
    @Id
    private String messageId;
    
    private String text; // This will be encrypted
    private String from; // "customer" or "admin"
    private LocalDateTime timestamp;
    private String status; // "pending" or "replied"
    
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.status = "pending";
    }
    
    public Message(String text, String from) {
        this();
        this.text = text;
        this.from = from;
    }
    
    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
