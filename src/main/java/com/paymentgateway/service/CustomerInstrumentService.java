package com.paymentgateway.service;

import com.paymentgateway.model.PaymentInstrument;
import java.util.List;
import java.util.UUID;

public interface CustomerInstrumentService {
    List<PaymentInstrument> getInstrumentsForUser(UUID userId);
}
