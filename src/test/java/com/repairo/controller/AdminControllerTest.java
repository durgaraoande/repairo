package com.repairo.controller;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import com.repairo.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private MessageService messageService;

    @MockBean
    private MongoEncryptionConfig encryptionConfig;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDashboard() throws Exception {
        // Given
        when(customerRepository.count()).thenReturn(10L);
        when(customerRepository.countByRepairStatus(RepairStatus.IN_PROGRESS)).thenReturn(3L);
        when(customerRepository.countCustomersWithPendingMessages()).thenReturn(2L);
        when(customerRepository.findByLastInteractionAfter(any(LocalDateTime.class))).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("totalCustomers", 10L))
                .andExpect(model().attribute("activeRepairs", 3L))
                .andExpect(model().attribute("pendingMessages", 2L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCustomers() throws Exception {
        // Given
        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setPhone("encrypted-phone");
        customer.setIssue("encrypted-issue");
        
        when(customerRepository.findAll()).thenReturn(Arrays.asList(customer));
        when(encryptionConfig.decrypt("encrypted-phone")).thenReturn("1234567890");
        when(encryptionConfig.decrypt("encrypted-issue")).thenReturn("Screen broken");

        // When & Then
        mockMvc.perform(get("/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customers"))
                .andExpect(model().attributeExists("customers"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCustomersWithSearch() throws Exception {
        // Given
        Customer customer = new Customer();
        customer.setName("John Doe");
        
        when(customerRepository.findByNameContainingIgnoreCase("John")).thenReturn(Arrays.asList(customer));
        when(encryptionConfig.decrypt(any())).thenReturn("decrypted");

        // When & Then
        mockMvc.perform(get("/admin/customers").param("search", "John"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/customers"))
                .andExpect(model().attribute("search", "John"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testMessages() throws Exception {
        // Given
        when(customerRepository.findCustomersWithPendingMessages()).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get("/admin/messages"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/messages"))
                .andExpect(model().attributeExists("customers"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testRepairs() throws Exception {
        // Given
        when(customerRepository.findAll()).thenReturn(Arrays.asList());

        // When & Then
        mockMvc.perform(get("/admin/repairs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/repairs"))
                .andExpect(model().attributeExists("customers"))
                .andExpect(model().attributeExists("repairStatuses"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateRepairStatus() throws Exception {
        // Given
        Customer customer = new Customer();
        customer.setCustomerId("test-id");
        when(customerRepository.findById("test-id")).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        // When & Then
        mockMvc.perform(post("/admin/update-status")
                .with(csrf())
                .param("customerId", "test-id")
                .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        verify(customerRepository).save(customer);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateRepairStatus_CustomerNotFound() throws Exception {
        // Given
        when(customerRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(post("/admin/update-status")
                .with(csrf())
                .param("customerId", "non-existent")
                .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(content().string("error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSendMessage() throws Exception {
        // When & Then
        mockMvc.perform(post("/admin/send-message")
                .with(csrf())
                .param("customerId", "test-id")
                .param("message", "Test message"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        verify(messageService).sendReplyMessage("test-id", "Test message");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testSendMessage_ServiceThrowsException() throws Exception {
        // Given
        doThrow(new RuntimeException("Service error")).when(messageService).sendReplyMessage(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/admin/send-message")
                .with(csrf())
                .param("customerId", "test-id")
                .param("message", "Test message"))
                .andExpect(status().isOk())
                .andExpect(content().string("error"));
    }

    @Test
    void testDashboard_Unauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized()); // Expect 401 Unauthorizedstatus().isForbidden()).or(status().is3xxRedirection()));
    }
}
