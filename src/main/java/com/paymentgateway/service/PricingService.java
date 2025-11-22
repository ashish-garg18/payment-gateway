package com.paymentgateway.service;

public interface PricingService {
    Double calculateFee(String vendorId, Double amount);
}
