package com.repairo.dto;

import jakarta.validation.constraints.NotBlank;

public class SendMessageRequest {
    @NotBlank(message = "Customer ID is required")
    private String customerId;
    
    @NotBlank(message = "Message is required")
    private String message;
    
    public SendMessageRequest() {}
    
    public SendMessageRequest(String customerId, String message) {
        this.customerId = customerId;
        this.message = message;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}