package com.paymentgateway.controller;

import com.paymentgateway.generated.api.PaymentsApi;
import com.paymentgateway.generated.model.MerchantConfigRequest;
import com.paymentgateway.generated.model.MerchantConfigResponse;
import com.paymentgateway.model.MerchantPaymentConfig;
import com.paymentgateway.service.impl.MerchantConfigServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for merchant payment configuration onboarding.
 * Implements OpenAPI generated interface.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class MerchantConfigController implements PaymentsApi {

    private final MerchantConfigServiceImpl merchantConfigService;

    @Override
    public ResponseEntity<MerchantConfigResponse> onboardMerchantConfig(MerchantConfigRequest request) {
        log.info("Merchant config onboarding request - merchantId: {}, methodId: {}",
                request.getMerchantId(), request.getMethodId());

        // Create entity from request
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        config.setMerchantId(request.getMerchantId());
        config.setMethodId(request.getMethodId());
        config.setSupportedNetworks(request.getSupportedNetworks());
        config.setMinAmount(request.getMinAmount() != null ? BigDecimal.valueOf(request.getMinAmount()) : null);
        config.setMaxAmount(request.getMaxAmount() != null ? BigDecimal.valueOf(request.getMaxAmount()) : null);
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        // Save to database
        MerchantPaymentConfig savedConfig = merchantConfigService.saveConfig(config);

        // Build response
        MerchantConfigResponse response = toResponse(savedConfig);
        response.setStatus("SUCCESS");
        response.setMessage("Merchant payment configuration saved successfully");

        log.info("Merchant config saved - merchantId: {}, methodId: {}, configId: {}",
                savedConfig.getMerchantId(), savedConfig.getMethodId(), savedConfig.getConfigId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<MerchantConfigResponse>> getMerchantConfigs(UUID merchantId) {
        log.info("Fetching merchant configs - merchantId: {}", merchantId);

        List<MerchantPaymentConfig> configs = merchantConfigService.getAllConfigs(merchantId);

        List<MerchantConfigResponse> responses = configs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<MerchantConfigResponse> getMerchantConfig(UUID merchantId, String methodId) {
        log.info("Fetching merchant config - merchantId: {}, methodId: {}", merchantId, methodId);

        MerchantPaymentConfig config = merchantConfigService.getConfig(merchantId, methodId);

        if (config == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(config));
    }

    private MerchantConfigResponse toResponse(MerchantPaymentConfig config) {
        MerchantConfigResponse response = new MerchantConfigResponse();
        response.setConfigId(config.getConfigId());
        response.setMerchantId(config.getMerchantId());
        response.setMethodId(config.getMethodId());
        response.setSupportedNetworks(config.getSupportedNetworks());
        response.setMinAmount(config.getMinAmount() != null ? config.getMinAmount().doubleValue() : null);
        response.setMaxAmount(config.getMaxAmount() != null ? config.getMaxAmount().doubleValue() : null);
        response.setEnabled(config.getEnabled());
        return response;
    }
}
