package com.repairo.dto;

import java.time.LocalDateTime;

public class CheckNewMessagesResponse {
    private boolean hasNewMessages;
    private int newMessageCount;
    private int totalCustomers;
    private LocalDateTime timestamp;
    private Object customers; // Detailed customer data for UI updates
    
    public CheckNewMessagesResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public CheckNewMessagesResponse(boolean hasNewMessages, int newMessageCount, int totalCustomers, Object customers) {
        this();
        this.hasNewMessages = hasNewMessages;
        this.newMessageCount = newMessageCount;
        this.totalCustomers = totalCustomers;
        this.customers = customers;
    }
    
    public boolean isHasNewMessages() {
        return hasNewMessages;
    }
    
    public void setHasNewMessages(boolean hasNewMessages) {
        this.hasNewMessages = hasNewMessages;
    }
    
    public int getNewMessageCount() {
        return newMessageCount;
    }
    
    public void setNewMessageCount(int newMessageCount) {
        this.newMessageCount = newMessageCount;
    }
    
    public int getTotalCustomers() {
        return totalCustomers;
    }
    
    public void setTotalCustomers(int totalCustomers) {
        this.totalCustomers = totalCustomers;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Object getCustomers() {
        return customers;
    }
    
    public void setCustomers(Object customers) {
        this.customers = customers;
    }
}