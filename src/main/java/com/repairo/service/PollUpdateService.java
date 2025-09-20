package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.dto.CheckNewMessagesResponse;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PollUpdateService {
    
    private static final Logger logger = LoggerFactory.getLogger(PollUpdateService.class);
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private MongoEncryptionConfig encryptionConfig;
    
    // Cache for storing minimal update information
    private Map<String, CustomerUpdateInfo> lastKnownState = new HashMap<>();
    
    /**
     * Gets minimal diff response for efficient polling updates
     * @param lastChecked Timestamp of last check
     * @return CheckNewMessagesResponse with minimal data changes
     */
    public CheckNewMessagesResponse getMinimalUpdates(LocalDateTime lastChecked) {
        try {
            List<Customer> customers = customerRepository.findAll();
            List<CustomerUpdateInfo> updates = new ArrayList<>();
            int newMessageCount = 0;
            boolean hasNewMessages = false;
            
            for (Customer customer : customers) {
                try {
                    CustomerUpdateInfo currentInfo = createCustomerUpdateInfo(customer);
                    String customerId = customer.getCustomerId();
                    
                    CustomerUpdateInfo lastKnown = lastKnownState.get(customerId);
                    
                    // Check if customer has updates since last check
                    boolean hasUpdates = false;
                    
                    if (lastKnown == null || hasChanges(lastKnown, currentInfo)) {
                        hasUpdates = true;
                        lastKnownState.put(customerId, currentInfo);
                    }
                    
                    // Check for new messages since lastChecked
                    if (customer.getMessages() != null && lastChecked != null) {
                        for (Message message : customer.getMessages()) {
                            if (message.getTimestamp() != null && 
                                message.getTimestamp().isAfter(lastChecked) &&
                                "customer".equals(message.getFrom())) {
                                newMessageCount++;
                                hasNewMessages = true;
                                hasUpdates = true;
                                break;
                            }
                        }
                    }
                    
                    // Only include customers with updates in the response
                    if (hasUpdates) {
                        updates.add(currentInfo);
                    }
                    
                } catch (Exception e) {
                    logger.warn("Error processing customer {} for updates: {}", 
                               customer.getCustomerId(), e.getMessage());
                }
            }
            
            // Build minimal response
            CheckNewMessagesResponse response = new CheckNewMessagesResponse();
            response.setHasNewMessages(hasNewMessages);
            response.setNewMessageCount(newMessageCount);
            response.setTotalCustomers(customers.size());
            
            // Only include customers with actual updates to minimize payload
            if (!updates.isEmpty()) {
                response.setCustomers(convertToMinimalCustomerData(updates));
            }
            
            logger.debug("Poll update complete. New messages: {}, Updated customers: {}", 
                        newMessageCount, updates.size());
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error getting minimal updates", e);
            CheckNewMessagesResponse errorResponse = new CheckNewMessagesResponse();
            return errorResponse;
        }
    }
    
    /**
     * Creates minimal customer update information
     */
    private CustomerUpdateInfo createCustomerUpdateInfo(Customer customer) {
        CustomerUpdateInfo info = new CustomerUpdateInfo();
        info.customerId = customer.getCustomerId();
        info.name = customer.getName();
        info.repairStatus = customer.getRepairStatus();
        info.lastInteraction = customer.getLastInteraction();
        info.messageCount = customer.getMessages() != null ? customer.getMessages().size() : 0;
        
        // Get timestamp of last message for quick comparison
        if (customer.getMessages() != null && !customer.getMessages().isEmpty()) {
            info.lastMessageTime = customer.getMessages().get(customer.getMessages().size() - 1).getTimestamp();
        }
        
        return info;
    }
    
    /**
     * Checks if customer info has changed
     */
    private boolean hasChanges(CustomerUpdateInfo oldInfo, CustomerUpdateInfo newInfo) {
        return !oldInfo.repairStatus.equals(newInfo.repairStatus) ||
               !oldInfo.lastInteraction.equals(newInfo.lastInteraction) ||
               oldInfo.messageCount != newInfo.messageCount ||
               (oldInfo.lastMessageTime != null && newInfo.lastMessageTime != null &&
                !oldInfo.lastMessageTime.equals(newInfo.lastMessageTime));
    }
    
    /**
     * Converts update info to minimal customer data for response
     */
    private List<Map<String, Object>> convertToMinimalCustomerData(List<CustomerUpdateInfo> updates) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (CustomerUpdateInfo info : updates) {
            Map<String, Object> customerData = new HashMap<>();
            customerData.put("customerId", info.customerId);
            customerData.put("name", info.name);
            customerData.put("repairStatus", info.repairStatus);
            customerData.put("lastInteraction", info.lastInteraction);
            customerData.put("messageCount", info.messageCount);
            customerData.put("lastMessageTime", info.lastMessageTime);
            
            result.add(customerData);
        }
        
        return result;
    }
    
    /**
     * Clears the cache - useful for testing or when full refresh is needed
     */
    public void clearCache() {
        lastKnownState.clear();
        logger.info("Poll update cache cleared");
    }
    
    /**
     * Inner class to hold minimal customer update information
     */
    private static class CustomerUpdateInfo {
        String customerId;
        String name;
        com.repairo.model.RepairStatus repairStatus;
        LocalDateTime lastInteraction;
        int messageCount;
        LocalDateTime lastMessageTime;
    }
}