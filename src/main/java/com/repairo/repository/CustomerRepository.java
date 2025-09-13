package com.repairo.repository;

import com.repairo.model.Customer;
import com.repairo.model.RepairStatus;
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
    
    List<Customer> findByNameContainingIgnoreCase(String name);
    
    @Query("{'messages.status': 'pending'}")
    List<Customer> findCustomersWithPendingMessages();
    
    List<Customer> findByLastInteractionAfter(LocalDateTime dateTime);
    
    long countByRepairStatus(RepairStatus repairStatus);
    
    @Query(value = "{ 'hasPendingMessages' : true }", count = true)
    long countCustomersWithPendingMessages();
}
