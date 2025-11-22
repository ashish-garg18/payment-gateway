package com.paymentgateway.service.impl;

import com.paymentgateway.model.VendorHealth;
import com.paymentgateway.repository.VendorHealthRepository;
import com.paymentgateway.service.VendorAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VendorAvailabilityServiceImpl implements VendorAvailabilityService {

    private final VendorHealthRepository vendorHealthRepository;

    @Override
    public List<VendorHealth> getAvailableVendors() {
        return vendorHealthRepository.findByIsDownFalse();
    }
}
