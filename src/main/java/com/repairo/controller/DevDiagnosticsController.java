package com.repairo.controller;

import com.repairo.dto.ApiResponse;
import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
import com.repairo.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@Profile("dev")
@RequestMapping("/admin")
public class DevDiagnosticsController {
    private static final Logger log = LoggerFactory.getLogger(DevDiagnosticsController.class);
    private final CustomerRepository customerRepository;

    public DevDiagnosticsController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping(value = "/db-health", produces = "application/json")
    public ResponseEntity<ApiResponse<?>> dbHealth() {
        try {
            long total = customerRepository.count();
            long pending = customerRepository.countByRepairStatus(RepairStatus.PENDING);
            long inProgress = customerRepository.countByRepairStatus(RepairStatus.IN_PROGRESS);
            long completed = customerRepository.countByRepairStatus(RepairStatus.COMPLETED);

            List<Customer> sample = customerRepository.findAll().stream().limit(3).toList();
            record SampleCustomer(String customerId, RepairStatus repairStatus, Long version) {}
            List<SampleCustomer> sampleView = new ArrayList<>();
            for (Customer c : sample) {
                sampleView.add(new SampleCustomer(c.getCustomerId(), c.getRepairStatus(), c.getVersion()));
            }
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("ok", true);
            payload.put("totalCustomers", total);
            payload.put("pending", pending);
            payload.put("inProgress", inProgress);
            payload.put("completed", completed);
            payload.put("sample", sampleView);
            payload.put("timestamp", java.time.Instant.now().toString());
            return ResponseEntity.ok(ApiResponse.success(payload));
        } catch (Exception ex) {
            log.error("DB health check failed", ex);
            return ResponseEntity.internalServerError().body(ApiResponse.error("DB error: " + ex.getClass().getSimpleName() + " - " + ex.getMessage()));
        }
    }
}
