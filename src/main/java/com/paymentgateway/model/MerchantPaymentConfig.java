package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "merchant_payment_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPaymentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID configId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 50)
    private String methodId;

    @ElementCollection
    @CollectionTable(name = "merchant_supported_networks", joinColumns = @JoinColumn(name = "config_id"))
    @Column(name = "network")
    private List<String> supportedNetworks;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
