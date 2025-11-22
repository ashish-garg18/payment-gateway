package com.paymentgateway.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "payment_instruments")
@Data
public class PaymentInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "method_id")
    private String methodId;

    @Column(name = "masked_details")
    private String maskedDetails; // **** 1234

    @Column(name = "network")
    private String network; // VISA

    @Column(name = "issuer")
    private String issuer; // HDFC

    @Column(name = "status")
    private String status; // ACTIVE, BLOCKED
}
