package com.paymentgateway.controller;

import com.paymentgateway.generated.api.PaymentApi;
import com.paymentgateway.generated.model.PaymentRequest;
import com.paymentgateway.generated.model.PaymentResponse;
import com.paymentgateway.generated.model.PaymentStatusResponse;
import com.paymentgateway.service.PaymentService;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for payment processing.
 * Implements OpenAPI generated interface.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentController implements PaymentApi {

        private final PaymentService paymentService;

        @Override
        @Timed(value = "controller.requests", extraTags = { "domain", "payment", "controller", "PaymentController",
                        "method", "processPayment" })
        @Counted(value = "controller.requests.count", extraTags = { "domain", "payment", "controller",
                        "PaymentController", "method", "processPayment" })
        public ResponseEntity<PaymentResponse> processPayment(UUID xUserId, PaymentRequest paymentRequest) {
                log.info("Payment request - paymentId: {}, userId: {}",
                                paymentRequest.getPaymentId(), xUserId);

                PaymentResponse response = paymentService.processPayment(paymentRequest, xUserId);

                log.info("Payment response - paymentId: {}, txnId: {}, status: {}, retryable: {}, requiresNewInstrument: {}",
                                response.getPaymentId(), response.getTxnId(), response.getStatus(),
                                response.getRetryable(), response.getRequiresNewInstrument());

                return ResponseEntity.ok(response);
        }

        @Override
        @Timed(value = "controller.requests", extraTags = { "domain", "payment", "controller", "PaymentController",
                        "method", "getPaymentStatus" })
        @Counted(value = "controller.requests.count", extraTags = { "domain", "payment", "controller",
                        "PaymentController", "method", "getPaymentStatus" })
        public ResponseEntity<PaymentStatusResponse> getPaymentStatus(UUID paymentId) {
                log.info("Payment status query - paymentId: {}", paymentId);

                PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);

                log.info("Payment status response - paymentId: {}, status: {}, retryCount: {}",
                                paymentId, response.getStatus(), response.getRetryCount());

                return ResponseEntity.ok(response);
        }
}
