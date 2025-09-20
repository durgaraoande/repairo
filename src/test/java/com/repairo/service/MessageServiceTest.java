package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.OnboardingState;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private MongoEncryptionConfig encryptionConfig;

    @Mock
    private WhatsAppService whatsAppService;

    @InjectMocks
    private MessageService messageService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setCustomerId("test-id");
        testCustomer.setPhone("encrypted-phone");
        testCustomer.setOnboardingState(OnboardingState.NEW);
        testCustomer.setMessages(new ArrayList<>());
    }

    @Test
    void testProcessIncomingMessage_NewCustomer() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "Hi";
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(customerRepository.findAll()).thenReturn(new ArrayList<>());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testProcessIncomingMessage_ExistingCustomer() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "Hello again";
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(customerRepository).save(testCustomer);
        assertEquals(1, testCustomer.getMessages().size());
    }

    @Test
    void testProcessIncomingMessage_OnboardingFlow_NameStep() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "hi";
        testCustomer.setOnboardingState(OnboardingState.NEW);
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq(phoneNumber), contains("What's your name"));
        assertEquals(OnboardingState.AWAITING_NAME, testCustomer.getOnboardingState());
    }

    @Test
    void testProcessIncomingMessage_OnboardingFlow_IssueStep() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "John Doe";
        testCustomer.setOnboardingState(OnboardingState.AWAITING_NAME);
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq(phoneNumber), contains("describe the issue"));
        assertEquals("John Doe", testCustomer.getName());
        assertEquals(OnboardingState.AWAITING_ISSUE, testCustomer.getOnboardingState());
    }

    @Test
    void testProcessIncomingMessage_OnboardingFlow_PhoneModelStep() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "Screen is broken";
        testCustomer.setOnboardingState(OnboardingState.AWAITING_ISSUE);
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq(phoneNumber), contains("phone model"));
        assertEquals("encrypted-Screen is broken", testCustomer.getIssue());
        assertEquals(OnboardingState.AWAITING_PHONE_MODEL, testCustomer.getOnboardingState());
    }

    @Test
    void testProcessIncomingMessage_OnboardingFlow_Complete() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "iPhone 13";
        testCustomer.setOnboardingState(OnboardingState.AWAITING_PHONE_MODEL);
        testCustomer.setName("John Doe");
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq(phoneNumber), contains("Thank you"));
        assertEquals("iPhone 13", testCustomer.getPhoneModel());
        assertEquals(OnboardingState.COMPLETED, testCustomer.getOnboardingState());
        assertEquals(RepairStatus.PENDING, testCustomer.getRepairStatus());
    }

    @Test
    void testProcessIncomingMessage_StatusRequest() {
        // Given
        String phoneNumber = "1234567890";
        String messageText = "status";
        testCustomer.setOnboardingState(OnboardingState.COMPLETED);
        testCustomer.setRepairStatus(RepairStatus.IN_PROGRESS);
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        List<Customer> customers = List.of(testCustomer);
        when(customerRepository.findAll()).thenReturn(customers);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.processIncomingMessage(phoneNumber, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq(phoneNumber), contains("IN_PROGRESS"));
    }

    @Test
    void testSendReplyMessage() {
        // Given
        String customerId = "test-id";
        String messageText = "We'll contact you soon";
        when(encryptionConfig.encrypt(anyString())).thenAnswer(invocation -> "encrypted-" + invocation.getArgument(0));
        when(encryptionConfig.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            return encrypted.startsWith("encrypted-") ? encrypted.substring(10) : encrypted;
        });
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        messageService.sendReplyMessage(customerId, messageText);

        // Then
        verify(whatsAppService).sendMessage(eq("1234567890"), eq(messageText));
        verify(customerRepository).save(testCustomer);
        assertEquals(1, testCustomer.getMessages().size());
        assertEquals("admin", testCustomer.getMessages().get(0).getFrom());
    }

    @Test
    void testSendReplyMessage_CustomerNotFound() {
        // Given
        String customerId = "non-existent";
        String messageText = "Test message";
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> 
            messageService.sendReplyMessage(customerId, messageText));
        
        verify(whatsAppService, never()).sendMessage(anyString(), anyString());
    }
}
