package com.repairo.controller;

import com.repairo.config.MongoEncryptionConfig;
import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import com.repairo.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

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
            customer.setPhone(encryptionConfig.decrypt(customer.getPhone()));
            customer.setIssue(encryptionConfig.decrypt(customer.getIssue()));
        });
        
        model.addAttribute("customers", customers);
        model.addAttribute("search", search);
        return "admin/customers";
    }

    @GetMapping("/messages")
    public String messages(Model model) {
        List<Customer> customersWithMessages = customerRepository.findCustomersWithPendingMessages();
        
        // Decrypt message content for display
        customersWithMessages.forEach(customer -> {
            customer.getMessages().forEach(message -> {
                message.setText(encryptionConfig.decrypt(message.getText()));
            });
        });
        
        model.addAttribute("customers", customersWithMessages);
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
        messageService.sendReplyMessage(customerId, message);
        return "success";
    }
}
