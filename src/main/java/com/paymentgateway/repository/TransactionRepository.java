package com.paymentgateway.repository;

import com.paymentgateway.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    java.util.Optional<Transaction> findByPaymentId(UUID paymentId);
}
