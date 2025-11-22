package com.paymentgateway.service.impl;

import com.paymentgateway.model.PricingModel;
import com.paymentgateway.repository.PricingModelRepository;
import com.paymentgateway.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {

    private final PricingModelRepository pricingModelRepository;

    @Override
    public Double calculateFee(String vendorId, Double amount) {
        // In a real system, we would fetch the specific model for this vendor and
        // amount
        // For simplicity, we fetch all applicable models and filter in memory or DB
        List<PricingModel> models = pricingModelRepository.findApplicableModels(amount);

        Optional<PricingModel> model = models.stream()
                .filter(m -> m.getVendorId().equals(vendorId))
                .findFirst();

        if (model.isPresent()) {
            PricingModel pm = model.get();
            return (amount * pm.getFeePercent() / 100.0) + pm.getFixedFee();
        }
        return Double.MAX_VALUE; // High cost if no model found
    }
}
