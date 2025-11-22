package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_payment_id", columnList = "payment_id")
})
@Data
public class Transaction {

    @Id
    @Column(name = "txn_id")
    private UUID txnId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "method_id")
    private String methodId;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "currency")
    private String currency = "INR";

    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "status")
    private String status; // INITIATED, SUCCESS, FAILED

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
