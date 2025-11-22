package com.paymentgateway.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class PricingModelId implements Serializable {
    private String vendorId;
    private Double minAmount;
    private Double maxAmount;
}
