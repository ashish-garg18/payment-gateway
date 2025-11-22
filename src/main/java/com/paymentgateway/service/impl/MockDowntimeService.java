package com.paymentgateway.service.impl;

import com.paymentgateway.service.DowntimeService;
import org.springframework.stereotype.Service;

@Service
public class MockDowntimeService implements DowntimeService {

    @Override
    public boolean isInstrumentDown(String instrumentType, String issuer) {
        // Simulation: Randomly mark 5% of requests as down
        // Or specific hardcoded rules for testing
        if ("HDFC".equalsIgnoreCase(issuer) && "DEBIT_CARD".equalsIgnoreCase(instrumentType)) {
            // Simulate HDFC Debit Cards are down
            return true;
        }

        // Random 5% downtime simulation
        return Math.random() < 0.05;
    }
}
