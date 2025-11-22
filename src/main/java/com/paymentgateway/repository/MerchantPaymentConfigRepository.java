package com.paymentgateway.repository;

import com.paymentgateway.model.MerchantPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for merchant payment configuration.
 */
@Repository
public interface MerchantPaymentConfigRepository extends JpaRepository<MerchantPaymentConfig, UUID> {

    /**
     * Find configuration by merchant ID and method ID.
     */
    Optional<MerchantPaymentConfig> findByMerchantIdAndMethodId(UUID merchantId, String methodId);

    /**
     * Find all enabled configurations for a merchant.
     */
    List<MerchantPaymentConfig> findByMerchantIdAndEnabledTrue(UUID merchantId);

    /**
     * Find all configurations for a merchant.
     */
    List<MerchantPaymentConfig> findByMerchantId(UUID merchantId);
}
