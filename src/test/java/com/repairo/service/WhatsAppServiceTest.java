package com.repairo.service;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsAppServiceTest {

    private final CustomerRepository customerRepository = Mockito.mock(CustomerRepository.class);
    private final MongoEncryptionConfig encryptionConfig = new MongoEncryptionConfig();
    private final WhatsAppService whatsAppService = new WhatsAppService();

    @Test
    void testHandleIncomingMessage_NewCustomer() {
        when(customerRepository.findByPhone(any())).thenReturn(Optional.empty());

        String payload = "{\"phone\":\"1234567890\",\"text\":\"Hi\"}";
        whatsAppService.handleIncomingMessage(payload);

        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testHandleAutoReply() {
        Customer customer = new Customer();
        customer.setRepairStatus(RepairStatus.PENDING);

        when(customerRepository.findByPhone(any())).thenReturn(Optional.of(customer));

        String payload = "{\"phone\":\"1234567890\",\"text\":\"status\"}";
        whatsAppService.handleIncomingMessage(payload);

        verify(customerRepository).save(customer);
    }
}
