package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.Message;
import com.repairo.model.OnboardingState;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WhatsAppService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MongoEncryptionConfig encryptionConfig;

    @Value("${whatsapp.access.token:}")
    private String accessToken;

    @Value("${whatsapp.phone.number.id:}")
    private String phoneNumberId;

    @Value("${whatsapp.api.url:https://graph.facebook.com/v18.0}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void handleIncomingMessage(String phoneNumber, String messageText) {
        // Encrypt phone for lookup
        String encryptedPhone = encryptionConfig.encrypt(phoneNumber);
        
        // Find existing customer or create new one
        Customer customer = findCustomerByEncryptedPhone(encryptedPhone);
        if (customer == null) {
            customer = new Customer();
            customer.setPhone(encryptedPhone);
        }

        // Add message to customer
        Message message = new Message(encryptionConfig.encrypt(messageText), "customer");
        customer.addMessage(message);

        // Handle onboarding flow
        handleOnboardingFlow(customer, messageText);

        // Save updated customer
        customerRepository.save(customer);
    }

    private Customer findCustomerByEncryptedPhone(String encryptedPhone) {
        // Since we can't query encrypted fields directly, we need to check all customers
        List<Customer> allCustomers = customerRepository.findAll();
        for (Customer customer : allCustomers) {
            if (encryptedPhone.equals(customer.getPhone())) {
                return customer;
            }
        }
        return null;
    }

    private void handleOnboardingFlow(Customer customer, String messageText) {
        String decryptedPhone = encryptionConfig.decrypt(customer.getPhone());
        
        switch (customer.getOnboardingState()) {
            case NEW:
                if (messageText.toLowerCase().contains("hi") || messageText.toLowerCase().contains("hello")) {
                    sendMessage(decryptedPhone, "Hi! Welcome to Repairo 📱 What's your name?");
                    customer.setOnboardingState(OnboardingState.ASKED_NAME);
                }
                break;
                
            case ASKED_NAME:
                customer.setName(messageText.trim());
                sendMessage(decryptedPhone, "Nice to meet you, " + messageText + "! What issue are you facing with your phone?");
                customer.setOnboardingState(OnboardingState.ASKED_ISSUE);
                break;
                
            case ASKED_ISSUE:
                customer.setIssue(encryptionConfig.encrypt(messageText));
                sendMessage(decryptedPhone, "Got it! What's your phone model?");
                customer.setOnboardingState(OnboardingState.ASKED_PHONE_MODEL);
                break;
                
            case ASKED_PHONE_MODEL:
                customer.setPhoneModel(messageText.trim());
                customer.setOnboardingState(OnboardingState.COMPLETED);
                customer.setRepairStatus(RepairStatus.PENDING);
                sendMessage(decryptedPhone, "Perfect! We've created your repair request. Our team will get back to you soon. 🔧");
                break;
                
            case COMPLETED:
                handleAutoReply(customer, messageText);
                break;
        }
    }

    private void handleAutoReply(Customer customer, String messageText) {
        String decryptedPhone = encryptionConfig.decrypt(customer.getPhone());
        String lowerText = messageText.toLowerCase();
        
        if (lowerText.contains("status") || lowerText.contains("update")) {
            String statusMessage = "Your repair status is: " + customer.getRepairStatus().toString();
            sendMessage(decryptedPhone, statusMessage);
        } else {
            sendMessage(decryptedPhone, "Thank you for your message. We will get back to you shortly.");
        }
    }

    public void sendMessage(String toPhoneNumber, String message) {
        if (accessToken.isEmpty() || phoneNumberId.isEmpty()) {
            System.out.println("WhatsApp not configured. Would send: " + message + " to " + toPhoneNumber);
            return;
        }

        try {
            String url = apiUrl + "/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", toPhoneNumber);
            payload.put("type", "text");

            Map<String, String> text = new HashMap<>();
            text.put("body", message);
            payload.put("text", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForObject(url, request, String.class);

        } catch (Exception e) {
            System.err.println("Failed to send WhatsApp message: " + e.getMessage());
        }
    }
}
