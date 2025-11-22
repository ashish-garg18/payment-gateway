package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Table(name = "payment_methods")
@Data
public class PaymentMethod {

    @Id
    @Column(name = "method_id")
    private String methodId; // e.g., CREDIT_CARD, UPI

    @Column(name = "method_name")
    private String methodName;

    @ElementCollection
    @CollectionTable(name = "payment_method_networks", joinColumns = @JoinColumn(name = "method_id"))
    @Column(name = "network")
    private List<String> supportedNetworks; // VISA, MASTERCARD

    @Column(name = "active")
    private boolean active = true;
}
