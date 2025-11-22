package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "pricing_models")
@Data
@IdClass(PricingModelId.class)
public class PricingModel {

    @Id
    @Column(name = "vendor_id")
    private String vendorId;

    @Id
    @Column(name = "min_amount")
    private Double minAmount;

    @Id
    @Column(name = "max_amount")
    private Double maxAmount;

    @Column(name = "fee_percent")
    private Double feePercent;

    @Column(name = "fixed_fee")
    private Double fixedFee;
}
