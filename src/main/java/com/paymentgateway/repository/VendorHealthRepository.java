package com.paymentgateway.repository;

import com.paymentgateway.model.VendorHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorHealthRepository extends JpaRepository<VendorHealth, String> {
    List<VendorHealth> findByIsDownFalse();
}
