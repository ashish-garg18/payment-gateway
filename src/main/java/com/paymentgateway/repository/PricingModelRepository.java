package com.paymentgateway.repository;

import com.paymentgateway.model.PricingModel;
import com.paymentgateway.model.PricingModelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingModelRepository extends JpaRepository<PricingModel, PricingModelId> {
    @Query("SELECT p FROM PricingModel p WHERE p.minAmount <= :amount AND p.maxAmount >= :amount")
    List<PricingModel> findApplicableModels(Double amount);
}
