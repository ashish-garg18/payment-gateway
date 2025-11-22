package com.paymentgateway.service;

import com.paymentgateway.model.VendorHealth;
import java.util.List;

public interface VendorAvailabilityService {
    List<VendorHealth> getAvailableVendors();
}
