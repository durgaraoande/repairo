package com.repairo.controller;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.model.OnboardingState;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import com.repairo.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private MongoEncryptionConfig encryptionConfig;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Add statistics to the dashboard
        long totalCustomers = customerRepository.count();
        long activeRepairs = customerRepository.countByRepairStatus(RepairStatus.IN_PROGRESS);
        long pendingMessages = customerRepository.countCustomersWithPendingMessages();
        long completedToday = customerRepository.findByLastInteractionAfter(
            LocalDateTime.now().minusDays(1)).stream()
            .mapToLong(c -> c.getRepairStatus() == RepairStatus.COMPLETED ? 1 : 0)
            .sum();
        
        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("activeRepairs", activeRepairs);
        model.addAttribute("pendingMessages", pendingMessages);
        model.addAttribute("completedToday", completedToday);
        
        // Handle potential null return from repository
        Long pendingCount = customerRepository.countCustomersWithPendingMessages();
        model.addAttribute("pendingMessages", pendingCount != null ? pendingCount : 0L);
        
        // Recent customers
        List<Customer> recentCustomers = customerRepository.findByLastInteractionAfter(
            LocalDateTime.now().minusDays(7));
        model.addAttribute("recentCustomers", recentCustomers);
        
        return "admin/dashboard";
    }
    
    @GetMapping("/dashboard-test")
    public String dashboardTest() {
        return "admin/dashboard-test";
    }

    @GetMapping("/customers")
    public String customers(Model model, @RequestParam(required = false) String search) {
        List<Customer> customers;
        if (search != null && !search.isEmpty()) {
            customers = customerRepository.findByNameContainingIgnoreCase(search);
        } else {
            customers = customerRepository.findAll();
        }
        
        // Decrypt sensitive fields for display
        customers.forEach(customer -> {
            customer.setPhone(encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone"));
            customer.setIssue(encryptionConfig.decryptSensitiveField(customer.getIssue(), "issue"));
        });
        
        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        return "admin/customers";
    }

    @GetMapping("/messages")
    public String messages(Model model) {
        try {
            logger.info("Loading customers and messages");
            
            // Get all customers
            List<Customer> allCustomers = customerRepository.findAll();
            logger.info("Found {} customers", allCustomers.size());
            
            // Decrypt customer data and messages with error handling
            List<Customer> validCustomers = new ArrayList<>();
            for (Customer customer : allCustomers) {
                try {
                    // Decrypt customer data safely - only sensitive fields
                    customer.setName(customer.getName()); // Name is not encrypted
                    customer.setPhone(encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone"));
                    customer.setIssue(encryptionConfig.decryptSensitiveField(customer.getIssue(), "issue"));
                    
                    // Decrypt message content safely - only message text
                    if (customer.getMessages() != null) {
                        customer.getMessages().forEach(message -> {
                            message.setText(encryptionConfig.decryptSensitiveField(message.getText(), "message"));
                        });
                    }
                    
                    validCustomers.add(customer);
                } catch (Exception e) {
                    logger.warn("Skipping customer {} due to decryption error: {}", 
                               customer.getCustomerId(), e.getMessage());
                }
            }
            
            // Sort customers by latest message timestamp (most recent first)
            validCustomers.sort((c1, c2) -> {
                if (c1.getMessages().isEmpty() && c2.getMessages().isEmpty()) return 0;
                if (c1.getMessages().isEmpty()) return 1;
                if (c2.getMessages().isEmpty()) return -1;
                
                return c2.getMessages().get(c2.getMessages().size() - 1).getTimestamp()
                      .compareTo(c1.getMessages().get(c1.getMessages().size() - 1).getTimestamp());
            });
            
            model.addAttribute("customers", validCustomers);
            logger.info("Successfully loaded {} valid customers with messages", validCustomers.size());
            
        } catch (Exception e) {
            logger.error("Error loading customers and messages", e);
            model.addAttribute("customers", new ArrayList<>());
            model.addAttribute("error", "Failed to load messages: " + e.getMessage());
        }
        
        return "admin/messages";
    }
    
    @GetMapping("/messages-test")
    public String messagesTest(Model model) {
        logger.info("=== TEST ENDPOINT REACHED ===");
        model.addAttribute("customers", new ArrayList<>());
        return "admin/messages";
    }

    @GetMapping("/repairs")
    public String repairs(Model model) {
        List<Customer> customers = customerRepository.findAll();
        model.addAttribute("customers", customers);
        model.addAttribute("repairStatuses", RepairStatus.values());
        return "admin/repairs";
    }
    
    @PostMapping("/update-status")
    @ResponseBody
    public String updateRepairStatus(@RequestParam String customerId, @RequestParam String status) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setRepairStatus(RepairStatus.valueOf(status));
            customer.setLastInteraction(LocalDateTime.now());
            customerRepository.save(customer);
            return "success";
        }
        return "error";
    }
    
    @PostMapping("/send-message")
    @ResponseBody
    public String sendMessage(@RequestParam String customerId, @RequestParam String message) {
        try {
            messageService.sendReplyMessage(customerId, message);
            return "success";
        } catch (Exception e) {
            logger.error("Error sending message to customer {}: {}", customerId, e.getMessage());
            return "error: " + e.getMessage();
        }
    }
    
    @GetMapping("/check-new-messages")
    @ResponseBody
    public Map<String, Object> checkNewMessages(@RequestParam(required = false) String lastChecked) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.debug("Checking for new messages. lastChecked: {}", lastChecked);
            
            List<Customer> customers = customerRepository.findAll();
            List<Customer> validCustomers = new ArrayList<>();
            int newMessageCount = 0;
            boolean hasNewMessages = false;
            LocalDateTime lastCheckedTime = null;
            
            // Parse lastChecked parameter with better error handling
            if (lastChecked != null && !lastChecked.isEmpty()) {
                try {
                    // Handle both ISO format and custom format
                    if (lastChecked.contains("T")) {
                        lastCheckedTime = LocalDateTime.parse(lastChecked.substring(0, 19));
                    } else {
                        lastCheckedTime = LocalDateTime.parse(lastChecked);
                    }
                    logger.debug("Parsed lastCheckedTime: {}", lastCheckedTime);
                } catch (Exception e) {
                    logger.warn("Invalid lastChecked timestamp: {}, using current time", lastChecked);
                    lastCheckedTime = LocalDateTime.now().minusMinutes(1); // Default to 1 minute ago
                }
            } else {
                // If no timestamp provided, check for messages in the last minute
                lastCheckedTime = LocalDateTime.now().minusMinutes(1);
                logger.debug("No lastChecked provided, using: {}", lastCheckedTime);
            }
            
            for (Customer customer : customers) {
                try {
                    // Decrypt and process customer messages
                    String decryptedPhone = encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone");
                    
                    if (customer.getMessages() != null && !customer.getMessages().isEmpty()) {
                        List<Message> decryptedMessages = new ArrayList<>();
                        
                        for (Message message : customer.getMessages()) {
                            try {
                                Message decryptedMessage = new Message();
                                decryptedMessage.setText(encryptionConfig.decryptSensitiveField(message.getText(), "message"));
                                decryptedMessage.setFrom(message.getFrom());
                                decryptedMessage.setTimestamp(message.getTimestamp());
                                decryptedMessages.add(decryptedMessage);
                                
                                // Check if this is a new message from customer
                                if (lastCheckedTime != null && 
                                    message.getTimestamp() != null &&
                                    message.getTimestamp().isAfter(lastCheckedTime) && 
                                    "customer".equals(message.getFrom())) {
                                    newMessageCount++;
                                    hasNewMessages = true;
                                    logger.debug("Found new message from customer {}: {} at {}", 
                                               customer.getCustomerId(), 
                                               decryptedMessage.getText().substring(0, Math.min(20, decryptedMessage.getText().length())), 
                                               message.getTimestamp());
                                }
                            } catch (Exception e) {
                                logger.warn("Failed to decrypt message for customer {}: {}", customer.getCustomerId(), e.getMessage());
                            }
                        }
                        
                        customer.setMessages(decryptedMessages);
                        customer.setPhone(decryptedPhone);
                    }
                    
                    if (customer.getName() != null) {
                        customer.setName(encryptionConfig.decryptSensitiveField(customer.getName(), "name"));
                    }
                    
                    validCustomers.add(customer);
                    
                } catch (Exception e) {
                    logger.warn("Skipping customer {} due to decryption error: {}", 
                               customer.getCustomerId(), e.getMessage());
                }
            }
            
            // Sort customers by latest message timestamp (most recent first)
            validCustomers.sort((c1, c2) -> {
                if (c1.getMessages() == null || c1.getMessages().isEmpty()) {
                    if (c2.getMessages() == null || c2.getMessages().isEmpty()) return 0;
                    return 1;
                }
                if (c2.getMessages() == null || c2.getMessages().isEmpty()) return -1;
                
                LocalDateTime time1 = c1.getMessages().get(c1.getMessages().size() - 1).getTimestamp();
                LocalDateTime time2 = c2.getMessages().get(c2.getMessages().size() - 1).getTimestamp();
                
                if (time1 == null && time2 == null) return 0;
                if (time1 == null) return 1;
                if (time2 == null) return -1;
                
                return time2.compareTo(time1);
            });
            
            response.put("success", true);
            response.put("newMessageCount", newMessageCount);
            response.put("hasNewMessages", hasNewMessages);
            response.put("totalCustomers", validCustomers.size());
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("customers", validCustomers); // Always return customers for UI updates
            
            logger.debug("Check complete. New messages: {}, Total customers: {}", newMessageCount, validCustomers.size());
            
        } catch (Exception e) {
            logger.error("Error checking for new messages", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }
}
