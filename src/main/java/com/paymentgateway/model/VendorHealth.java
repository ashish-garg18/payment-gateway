package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendor_health")
@Data
public class VendorHealth {

    @Id
    @Column(name = "vendor_id")
    private String vendorId;

    @Column(name = "uptime_score")
    private Double uptimeScore; // 0.00 to 100.00

    @Column(name = "error_rate")
    private Double errorRate;

    @Column(name = "is_down")
    private boolean isDown;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
