package com.paymentgateway.controller;

import com.paymentgateway.generated.api.CheckoutApi;
import com.paymentgateway.generated.model.CheckoutRequest;
import com.paymentgateway.generated.model.CheckoutResponse;
import com.paymentgateway.service.CheckoutService;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for checkout operations.
 * Implements OpenAPI generated interface.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CheckoutController implements CheckoutApi {

    private final CheckoutService checkoutService;

    @Override
    @Timed(value = "controller.requests", extraTags = { "domain", "checkout", "controller", "CheckoutController",
            "method", "getCheckoutOptions" })
    @Counted(value = "controller.requests.count", extraTags = { "domain", "checkout", "controller",
            "CheckoutController", "method", "getCheckoutOptions" })
    public ResponseEntity<CheckoutResponse> getCheckoutOptions(
            UUID xUserId,
            UUID xIdempotencyKey,
            CheckoutRequest checkoutRequest) {

        UUID paymentId = checkoutRequest.getPaymentId(); // Can be null for first-time checkout

        log.info("Checkout request - checkoutId: {}, userId: {}, idempotencyKey: {}, paymentId: {}",
                checkoutRequest.getCheckoutId(), xUserId, xIdempotencyKey, paymentId);

        CheckoutResponse response = checkoutService.getCheckoutOptions(
                checkoutRequest.getCheckoutId(),
                xIdempotencyKey,
                checkoutRequest.getMerchant().getMerchantId(),
                xUserId,
                checkoutRequest.getPayment().getAmount(),
                checkoutRequest.getMerchant().getMcc(),
                paymentId // Pass optional paymentId for retry scenarios
        );

        log.info("Checkout response - checkoutId: {}, paymentId: {}, methods: {}",
                checkoutRequest.getCheckoutId(), response.getPaymentId(),
                response.getPaymentMethods().size());

        return ResponseEntity.ok(response);
    }
}
