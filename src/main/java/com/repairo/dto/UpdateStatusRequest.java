package com.repairo.dto;

import com.repairo.model.RepairStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateStatusRequest {
    @NotBlank(message = "Customer ID is required")
    private String customerId;
    
    @NotNull(message = "Repair status is required")
    private RepairStatus status;
    
    private Long version; // For optimistic locking
    
    public UpdateStatusRequest() {}
    
    public UpdateStatusRequest(String customerId, RepairStatus status, Long version) {
        this.customerId = customerId;
        this.status = status;
        this.version = version;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public RepairStatus getStatus() {
        return status;
    }
    
    public void setStatus(RepairStatus status) {
        this.status = status;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
}