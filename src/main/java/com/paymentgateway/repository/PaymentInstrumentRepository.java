package com.paymentgateway.repository;

import com.paymentgateway.model.PaymentInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentInstrumentRepository extends JpaRepository<PaymentInstrument, UUID> {
    List<PaymentInstrument> findByUserId(UUID userId);
}
