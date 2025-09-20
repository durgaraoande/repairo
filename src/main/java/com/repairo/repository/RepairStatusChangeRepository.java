package com.repairo.repository;

import com.repairo.model.RepairStatusChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RepairStatusChangeRepository extends MongoRepository<RepairStatusChange, String> {
    
    List<RepairStatusChange> findByCustomerId(String customerId);
    Page<RepairStatusChange> findByCustomerId(String customerId, Pageable pageable);
    
    List<RepairStatusChange> findByChangedBy(String changedBy);
    Page<RepairStatusChange> findByChangedBy(String changedBy, Pageable pageable);
    
    List<RepairStatusChange> findByChangedAtBetween(LocalDateTime start, LocalDateTime end);
    Page<RepairStatusChange> findByChangedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    List<RepairStatusChange> findByCustomerIdOrderByChangedAtDesc(String customerId);
    
    Page<RepairStatusChange> findAllByOrderByChangedAtDesc(Pageable pageable);
}