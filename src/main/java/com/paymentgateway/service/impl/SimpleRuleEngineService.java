package com.paymentgateway.service.impl;

import com.paymentgateway.model.MerchantPaymentConfig;
import com.paymentgateway.model.PaymentInstrument;
import com.paymentgateway.model.PaymentMethod;
import com.paymentgateway.service.MerchantConfigService;
import com.paymentgateway.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimpleRuleEngineService implements RuleEngineService {

    private final MerchantConfigService merchantConfigService;

    @Override
    public boolean isEligible(PaymentMethod method, PaymentInstrument instrument, UUID merchantId, String mcc,
            Double amount) {
        return getIneligibilityReason(method, instrument, merchantId, mcc, amount) == null;
    }

    @Override
    public String getIneligibilityReason(PaymentMethod method, PaymentInstrument instrument, UUID merchantId,
            String mcc, Double amount) {

        // 1. Fetch Merchant-Specific Configuration
        MerchantPaymentConfig merchantConfig = merchantConfigService.getConfig(merchantId, method.getMethodId());

        if (merchantConfig == null || !Boolean.TRUE.equals(merchantConfig.getEnabled())) {
            return "Payment method not enabled for this merchant";
        }

        // 2. MCC Restrictions (e.g., Gambling MCCs block Credit Cards)
        if ("6011".equals(mcc) && "CREDIT_CARD".equals(method.getMethodId())) {
            return "Credit Cards not allowed for this Merchant Category";
        }

        // 3. Network Compatibility (using MERCHANT config, not global method)
        if (instrument != null && merchantConfig.getSupportedNetworks() != null) {
            if (!merchantConfig.getSupportedNetworks().contains(instrument.getNetwork())) {
                return "Network " + instrument.getNetwork() + " not supported by merchant";
            }
        }

        // 4. Amount Limits (merchant-specific limits)
        if (merchantConfig.getMinAmount() != null && amount < merchantConfig.getMinAmount().doubleValue()) {
            return "Amount below merchant's minimum of " + merchantConfig.getMinAmount();
        }
        if (merchantConfig.getMaxAmount() != null && amount > merchantConfig.getMaxAmount().doubleValue()) {
            return "Amount exceeds merchant's maximum of " + merchantConfig.getMaxAmount();
        }

        // 5. Global Method Limits (e.g., UPI regulatory limits)
        if ("UPI".equals(method.getMethodId()) && amount > 100000) {
            return "Amount exceeds UPI regulatory limit of 1,00,000";
        }

        return null;
    }
}
