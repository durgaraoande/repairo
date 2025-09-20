package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.model.OnboardingState;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MongoEncryptionConfig encryptionConfig;

    @Autowired
    private WhatsAppService whatsAppService;

    public void processIncomingMessage(String phoneNumber, String messageText) {
        Customer customer = findOrCreateCustomer(phoneNumber);
        
        // Encrypt message text before storing (only sensitive field)
        Message message = new Message();
        message.setText(encryptionConfig.encryptSensitiveField(messageText, "message"));
        message.setFrom("customer");
        
        customer.addMessage(message);
        
        handleOnboardingFlow(customer, messageText);
        
        customerRepository.save(customer);
    }

    private Customer findOrCreateCustomer(String phoneNumber) {
        // For demo purposes, find by encrypted phone or create new
        String encryptedPhone = encryptionConfig.encryptSensitiveField(phoneNumber, "phone");
        List<Customer> allCustomers = customerRepository.findAll();
        
        for (Customer c : allCustomers) {
            if (encryptedPhone.equals(c.getPhone())) {
                return c;
            }
        }
        
        // Create new customer
        Customer customer = new Customer();
        customer.setPhone(encryptedPhone);
        return customer;
    }

    private void handleOnboardingFlow(Customer customer, String messageText) {
        OnboardingState state = customer.getOnboardingState();
        String decryptedPhone = encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone");

        try {
            switch (state) {
                case NEW:
                    if (messageText.toLowerCase().contains("hi") || messageText.toLowerCase().contains("hello")) {
                        whatsAppService.sendMessage(decryptedPhone, "Hello! Welcome to our repair service. What's your name?");
                        customer.setOnboardingState(OnboardingState.AWAITING_NAME);
                    }
                    break;
                    
                case AWAITING_NAME:
                    customer.setName(messageText); // Name is not encrypted
                    whatsAppService.sendMessage(decryptedPhone, "Nice to meet you, " + messageText + "! Please describe the issue with your device.");
                    customer.setOnboardingState(OnboardingState.AWAITING_ISSUE);
                    break;
                    
                case AWAITING_ISSUE:
                    customer.setIssue(encryptionConfig.encryptSensitiveField(messageText, "issue"));
                    whatsAppService.sendMessage(decryptedPhone, "Got it! What's your phone model?");
                    customer.setOnboardingState(OnboardingState.AWAITING_PHONE_MODEL);
                    break;
                    
                case AWAITING_PHONE_MODEL:
                    customer.setPhoneModel(messageText); // Phone model is not encrypted
                    customer.setRepairStatus(RepairStatus.PENDING);
                    customer.setOnboardingState(OnboardingState.COMPLETED);
                    whatsAppService.sendMessage(decryptedPhone, "Thank you! We've received your repair request. You can check your status anytime by typing 'status'.");
                    break;
                    
                case COMPLETED:
                    if (messageText.toLowerCase().contains("status")) {
                        whatsAppService.sendMessage(decryptedPhone, "Your repair status: " + customer.getRepairStatus());
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to send WhatsApp message during onboarding flow for phone {}: {}", decryptedPhone, e.getMessage());
            // Continue processing even if message sending fails
        }
    }

    public void sendReplyMessage(String customerId, String messageText) {
        Optional<Customer> optionalCustomer = customerRepository.findById(customerId);
        if (optionalCustomer.isEmpty()) {
            throw new RuntimeException("Customer not found: " + customerId);
        }
        
        Customer customer = optionalCustomer.get();
        String decryptedPhone = encryptionConfig.decryptSensitiveField(customer.getPhone(), "phone");
        
        try {
            // Send via WhatsApp
            whatsAppService.sendMessage(decryptedPhone, messageText);
            logger.info("Successfully sent WhatsApp reply message to customer {}", customerId);
        } catch (Exception e) {
            logger.error("Failed to send WhatsApp reply message to customer {}: {}", customerId, e.getMessage());
            // Re-throw the exception to let the controller handle it
            throw new RuntimeException("WhatsApp message sending failed", e);
        }
        
        // Store admin reply in database only if WhatsApp sending succeeded
        Message adminMessage = new Message();
        adminMessage.setText(encryptionConfig.encryptSensitiveField(messageText, "message"));
        adminMessage.setFrom("admin");
        customer.addMessage(adminMessage);
        
        customerRepository.save(customer);
    }
}
