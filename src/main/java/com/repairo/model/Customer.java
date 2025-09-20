package com.repairo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "customers")
public class Customer implements Persistable<String> {
    @Id
    private String customerId;
    
    @Version
    private Long version;
    
    // Plaintext fields for filtering and display
    private String name;
    private String phoneModel;
    private RepairStatus repairStatus;
    private OnboardingState onboardingState;
    private LocalDateTime lastInteraction;
    
    // Encrypted fields (sensitive data)
    private String phone; // Encrypted
    private String issue; // Encrypted
    
    private List<Message> messages;
    
    public Customer() {
        this.messages = new ArrayList<>();
        this.repairStatus = RepairStatus.PENDING;
        this.onboardingState = OnboardingState.NEW;
        this.lastInteraction = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    
    public String getPhoneModel() { return phoneModel; }
    public void setPhoneModel(String phoneModel) { this.phoneModel = phoneModel; }
    
    public OnboardingState getOnboardingState() { return onboardingState; }
    public void setOnboardingState(OnboardingState onboardingState) { this.onboardingState = onboardingState; }
    
    public RepairStatus getRepairStatus() { return repairStatus; }
    public void setRepairStatus(RepairStatus repairStatus) { this.repairStatus = repairStatus; }
    
    public LocalDateTime getLastInteraction() { return lastInteraction; }
    public void setLastInteraction(LocalDateTime lastInteraction) { this.lastInteraction = lastInteraction; }
    
    public void addMessage(Message message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.lastInteraction = LocalDateTime.now();
    }

    // Persistable implementation
    @Override
    public String getId() {
        return this.customerId;
    }

    @Override
    public boolean isNew() {
        // Consider entity new only if id is null
        return this.customerId == null;
    }
}
