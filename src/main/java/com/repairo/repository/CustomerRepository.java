package com.repairo.repository;

import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {
    Optional<Customer> findByPhone(String phone);
    
    List<Customer> findByRepairStatus(RepairStatus repairStatus);
    Page<Customer> findByRepairStatus(RepairStatus repairStatus, Pageable pageable);
    
    List<Customer> findByNameContainingIgnoreCase(String name);
    Page<Customer> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    Page<Customer> findAll(Pageable pageable);
    
    @Query("{'messages.status': 'pending'}")
    List<Customer> findCustomersWithPendingMessages();
    
    List<Customer> findByLastInteractionAfter(LocalDateTime dateTime);
    Page<Customer> findByLastInteractionAfter(LocalDateTime dateTime, Pageable pageable);
    
    long countByRepairStatus(RepairStatus repairStatus);
    
    @Query(value = "{ 'hasPendingMessages' : true }", count = true)
    long countCustomersWithPendingMessages();
}
