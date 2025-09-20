package com.repairo.controller;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.dto.*;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.model.RepairStatus;
import com.repairo.model.RepairStatusChange;
import com.repairo.repository.CustomerRepository;
import com.repairo.config.FeatureProperties;
import com.repairo.repository.RepairStatusChangeRepository;
import com.repairo.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
@Validated
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private RepairStatusChangeRepository repairStatusChangeRepository;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private MongoEncryptionConfig encryptionConfig;

    @Autowired
    private com.repairo.service.PollUpdateService pollUpdateService;

    @Autowired(required = false)
    private com.repairo.service.WebSocketEventPublisher webSocketEventPublisher;

    @Autowired(required = false)
    private FeatureProperties featureProperties;

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
        
        // Decrypt sensitive fields for recent customers
        recentCustomers.forEach(customer -> {
            customer.setPhone(encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone"));
            customer.setIssue(encryptionConfig.decryptSensitiveField(customer.getIssue(), "issue"));
        });
        
        model.addAttribute("recentCustomers", recentCustomers);
        
        return "admin/dashboard";
    }
    
    @GetMapping("/dashboard-test")
    public String dashboardTest() {
        return "admin/dashboard-test";
    }

    @GetMapping("/customers")
    public String customers(Model model, 
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(defaultValue = "lastInteraction") String sortBy,
                           @RequestParam(defaultValue = "desc") String sortDir) {
        
        // Create pageable with sorting
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<Customer> customerPage;
        if (search != null && !search.isEmpty()) {
            customerPage = customerRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            customerPage = customerRepository.findAll(pageable);
        }
        
        // Decrypt sensitive fields for display
        customerPage.getContent().forEach(customer -> {
            customer.setPhone(encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone"));
            customer.setIssue(encryptionConfig.decryptSensitiveField(customer.getIssue(), "issue"));
        });
        
        model.addAttribute("customersPage", customerPage);
        model.addAttribute("customers", customerPage.getContent());
        model.addAttribute("search", search);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("totalPages", customerPage.getTotalPages());
        model.addAttribute("totalElements", customerPage.getTotalElements());
        
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
        
        // Decrypt sensitive fields for display
        customers.forEach(customer -> {
            customer.setPhone(encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone"));
            customer.setIssue(encryptionConfig.decryptSensitiveField(customer.getIssue(), "issue"));
        });
        
        model.addAttribute("customers", customers);
        model.addAttribute("repairStatuses", RepairStatus.values());
        return "admin/repairs";
    }
    
    @PostMapping(value = "/update-status", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> updateRepairStatus(@Valid @RequestBody UpdateStatusRequest request,
                                                                  Authentication authentication) {
        try {
            Optional<Customer> customerOpt = customerRepository.findById(request.getCustomerId());
            if (customerOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Customer not found"));
            }
            
            Customer customer = customerOpt.get();
            
            // Check version for optimistic locking if provided
            if (request.getVersion() != null && customer.getVersion() != null && !customer.getVersion().equals(request.getVersion())) {
                return ResponseEntity.status(409).body(ApiResponse.error("Customer was modified by another user. Please refresh and try again."));
            }
            
            RepairStatus oldStatus = customer.getRepairStatus();
            customer.setRepairStatus(request.getStatus());
            customer.setLastInteraction(LocalDateTime.now());
            customerRepository.save(customer);
            
            // Record status change in audit log
            String username = authentication != null ? authentication.getName() : "system";
            if (featureProperties == null || featureProperties.isAuditStatus()) {
                RepairStatusChange statusChange = new RepairStatusChange(
                    request.getCustomerId(), oldStatus, request.getStatus(), username
                );
                repairStatusChangeRepository.save(statusChange);
            }

            logger.info("Status updated for customer {} from {} to {} by {}", 
                       request.getCustomerId(), oldStatus, request.getStatus(), username);
            
            if ((featureProperties == null || featureProperties.isWebsockets()) && webSocketEventPublisher != null) {
                webSocketEventPublisher.publishStatusChange(request.getCustomerId(), oldStatus.name(), request.getStatus().name());
            }
            return ResponseEntity.ok(ApiResponse.success("Status updated successfully"));
            
        } catch (Exception e) {
            logger.error("Error updating status for customer {}: {}", request.getCustomerId(), e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to update status: " + e.getMessage()));
        }
    }
    
    @PostMapping(value = "/send-message", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        try {
            messageService.sendReplyMessage(request.getCustomerId(), request.getMessage());
            if ((featureProperties == null || featureProperties.isWebsockets()) && webSocketEventPublisher != null) {
                String preview = request.getMessage();
                if (preview.length() > 40) preview = preview.substring(0, 40) + "...";
                webSocketEventPublisher.publishNewMessage(request.getCustomerId(), preview);
            }
            return ResponseEntity.ok(ApiResponse.success("Message sent successfully"));
        } catch (Exception e) {
            logger.error("Error sending message to customer {}: {}", request.getCustomerId(), e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("Failed to send message: " + e.getMessage()));
        }
    }
    
    @GetMapping("/check-new-messages")
    @ResponseBody
    public ResponseEntity<ApiResponse<CheckNewMessagesResponse>> checkNewMessages(@RequestParam(required = false) String lastChecked,
                                                                                  @RequestParam(name = "diff", required = false, defaultValue = "false") boolean diff) {
        try {
            logger.debug("Checking for new messages. lastChecked: {}", lastChecked);

            // If diff mode requested, leverage PollUpdateService for minimal response
            if (diff) {
                LocalDateTime lastCheckedTime = null;
                if (lastChecked != null && !lastChecked.isEmpty()) {
                    try {
                        if (lastChecked.contains("T")) {
                            lastCheckedTime = LocalDateTime.parse(lastChecked.substring(0, 19));
                        } else {
                            lastCheckedTime = LocalDateTime.parse(lastChecked);
                        }
                    } catch (Exception e) {
                        logger.warn("Invalid lastChecked for diff mode: {}", lastChecked);
                    }
                }
                CheckNewMessagesResponse minimal = pollUpdateService.getMinimalUpdates(lastCheckedTime);
                return ResponseEntity.ok(ApiResponse.success(minimal));
            }
            
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
            
            CheckNewMessagesResponse response = new CheckNewMessagesResponse(
                hasNewMessages, newMessageCount, validCustomers.size(), validCustomers
            );
            
            logger.debug("Check complete. New messages: {}, Total customers: {}", newMessageCount, validCustomers.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            logger.error("Error checking for new messages", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to check messages: " + e.getMessage()));
        }
    }

}
