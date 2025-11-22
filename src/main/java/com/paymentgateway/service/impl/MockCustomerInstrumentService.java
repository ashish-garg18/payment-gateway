package com.paymentgateway.service.impl;

import com.paymentgateway.model.PaymentInstrument;
import com.paymentgateway.repository.PaymentInstrumentRepository;
import com.paymentgateway.service.CustomerInstrumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MockCustomerInstrumentService implements CustomerInstrumentService {

    private final PaymentInstrumentRepository paymentInstrumentRepository;

    @Override
    public List<PaymentInstrument> getInstrumentsForUser(UUID userId) {
        // In a real system, this would call an external service.
        // Here we just fetch from our local DB for simulation.
        return paymentInstrumentRepository.findByUserId(userId);
    }
}
