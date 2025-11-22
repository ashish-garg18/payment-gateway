package com.paymentgateway.service.impl;

import com.paymentgateway.model.MerchantPaymentConfig;
import com.paymentgateway.repository.MerchantPaymentConfigRepository;
import com.paymentgateway.service.MerchantConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Database-backed implementation of MerchantConfigService.
 * Fetches merchant payment configuration from the database.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class MerchantConfigServiceImpl implements MerchantConfigService {

    private final MerchantPaymentConfigRepository repository;

    @Override
    public MerchantPaymentConfig getConfig(UUID merchantId, String methodId) {
        log.debug("Fetching merchant config from DB - merchantId: {}, methodId: {}", merchantId, methodId);
        return repository.findByMerchantIdAndMethodId(merchantId, methodId).orElse(null);
    }

    @Override
    public List<MerchantPaymentConfig> getEnabledConfigs(UUID merchantId) {
        log.debug("Fetching enabled merchant configs from DB - merchantId: {}", merchantId);
        return repository.findByMerchantIdAndEnabledTrue(merchantId);
    }

    /**
     * Save or update merchant payment configuration.
     * Used by the onboarding API.
     */
    public MerchantPaymentConfig saveConfig(MerchantPaymentConfig config) {
        log.info("Saving merchant config to DB - merchantId: {}, methodId: {}",
                config.getMerchantId(), config.getMethodId());
        return repository.save(config);
    }

    /**
     * Get all configurations for a merchant (including disabled).
     */
    public List<MerchantPaymentConfig> getAllConfigs(UUID merchantId) {
        return repository.findByMerchantId(merchantId);
    }
}
