package com.repairo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "repair_status_changes")
public class RepairStatusChange {
    @Id
    private String id;
    
    private String customerId;
    private RepairStatus fromStatus;
    private RepairStatus toStatus;
    private String changedBy;
    private LocalDateTime changedAt;
    private String notes; // Optional field for additional context
    
    public RepairStatusChange() {
        this.changedAt = LocalDateTime.now();
    }
    
    public RepairStatusChange(String customerId, RepairStatus fromStatus, RepairStatus toStatus, String changedBy) {
        this();
        this.customerId = customerId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
    }
    
    public RepairStatusChange(String customerId, RepairStatus fromStatus, RepairStatus toStatus, String changedBy, String notes) {
        this(customerId, fromStatus, toStatus, changedBy);
        this.notes = notes;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public RepairStatus getFromStatus() {
        return fromStatus;
    }
    
    public void setFromStatus(RepairStatus fromStatus) {
        this.fromStatus = fromStatus;
    }
    
    public RepairStatus getToStatus() {
        return toStatus;
    }
    
    public void setToStatus(RepairStatus toStatus) {
        this.toStatus = toStatus;
    }
    
    public String getChangedBy() {
        return changedBy;
    }
    
    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
    
    public LocalDateTime getChangedAt() {
        return changedAt;
    }
    
    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}