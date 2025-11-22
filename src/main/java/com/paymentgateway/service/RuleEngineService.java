package com.paymentgateway.service;

import com.paymentgateway.model.PaymentInstrument;
import com.paymentgateway.model.PaymentMethod;
import java.util.UUID;

public interface RuleEngineService {
    boolean isEligible(PaymentMethod method, PaymentInstrument instrument, UUID merchantId, String mcc, Double amount);

    String getIneligibilityReason(PaymentMethod method, PaymentInstrument instrument, UUID merchantId, String mcc,
            Double amount);
}
