package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.model.OnboardingState;
import com.repairo.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MessageService {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private MongoEncryptionConfig encryptionConfig;
    
    @Autowired
    private WhatsAppService whatsAppService;
    
    public void processIncomingMessage(String phoneNumber, String messageText) {
        Customer customer = findOrCreateCustomer(phoneNumber);
        
        // Encrypt message text before storing
        Message message = new Message();
        message.setText(encryptionConfig.encrypt(messageText));
        message.setFrom("customer");
        
        customer.addMessage(message);
        
        // Handle onboarding flow
        handleOnboardingFlow(customer, messageText);
        
        customerRepository.save(customer);
    }
    
    private Customer findOrCreateCustomer(String phoneNumber) {
        // For demo purposes, find by encrypted phone or create new
        String encryptedPhone = encryptionConfig.encrypt(phoneNumber);
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
        String lowerMessage = messageText.toLowerCase().trim();
        String decryptedPhone = encryptionConfig.decrypt(customer.getPhone());
        
        switch (customer.getOnboardingState()) {
            case NEW:
                if (lowerMessage.contains("hi") || lowerMessage.contains("hello")) {
                    whatsAppService.sendMessage(decryptedPhone, 
                        "Hi! Welcome to Repairo 📱 What's your name?");
                    customer.setOnboardingState(OnboardingState.ASKED_NAME);
                }
                break;
                
            case ASKED_NAME:
                customer.setName(messageText);
                whatsAppService.sendMessage(decryptedPhone, 
                    "Nice to meet you, " + messageText + "! What issue are you facing with your phone?");
                customer.setOnboardingState(OnboardingState.ASKED_ISSUE);
                break;
                
            case ASKED_ISSUE:
                customer.setIssue(encryptionConfig.encrypt(messageText));
                whatsAppService.sendMessage(decryptedPhone, 
                    "Got it! What's your phone model?");
                customer.setOnboardingState(OnboardingState.ASKED_PHONE_MODEL);
                break;
                
            case ASKED_PHONE_MODEL:
                customer.setPhoneModel(messageText);
                customer.setOnboardingState(OnboardingState.COMPLETED);
                whatsAppService.sendMessage(decryptedPhone, 
                    "Perfect! We've created your repair request. Our team will get back to you soon. 🔧");
                break;
                
            case COMPLETED:
                if (lowerMessage.contains("status") || lowerMessage.contains("update")) {
                    String statusMessage = "Your repair status: " + customer.getRepairStatus().toString();
                    whatsAppService.sendMessage(decryptedPhone, statusMessage);
                }
                break;
        }
    }
    
    public void sendReplyMessage(String customerId, String messageText) {
        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            
            // Send via WhatsApp
            String decryptedPhone = encryptionConfig.decrypt(customer.getPhone());
            whatsAppService.sendMessage(decryptedPhone, messageText);
            
            // Store encrypted message
            Message message = new Message();
            message.setText(encryptionConfig.encrypt(messageText));
            message.setFrom("admin");
            message.setStatus("replied");
            
            customer.addMessage(message);
            customerRepository.save(customer);
        }
    }
}
