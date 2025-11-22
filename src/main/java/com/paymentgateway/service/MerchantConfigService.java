package com.paymentgateway.service;

import com.paymentgateway.model.MerchantPaymentConfig;

import java.util.List;
import java.util.UUID;

/**
 * Service for retrieving merchant-specific payment configuration.
 * Configuration is populated by external merchant onboarding team.
 */
public interface MerchantConfigService {

    /**
     * Get payment configuration for a specific merchant and payment method.
     *
     * @param merchantId The merchant identifier
     * @param methodId   The payment method ID (e.g., "CREDIT_CARD", "UPI")
     * @return Merchant payment configuration, or null if not configured
     */
    MerchantPaymentConfig getConfig(UUID merchantId, String methodId);

    /**
     * Get all enabled payment configurations for a merchant.
     *
     * @param merchantId The merchant identifier
     * @return List of enabled payment configurations
     */
    List<MerchantPaymentConfig> getEnabledConfigs(UUID merchantId);
}
