package com.paymentgateway.service;

import com.paymentgateway.generated.model.PaymentRequest;
import com.paymentgateway.generated.model.PaymentResponse;
import com.paymentgateway.generated.model.PaymentStatusResponse;
import com.paymentgateway.model.Transaction;
import com.paymentgateway.model.VendorHealth;
import com.paymentgateway.repository.TransactionRepository;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

        private final VendorAvailabilityService vendorAvailabilityService;
        private final PricingService pricingService;
        private final TransactionRepository transactionRepository;

        // In-memory cache for payment idempotency (in production, use Redis/Database)
        // Maps paymentId -> Transaction
        private final Map<UUID, Transaction> paymentCache = new ConcurrentHashMap<>();

        // Track failed vendors per paymentId for retry logic
        // Maps paymentId -> Set of failed vendorIds
        private final Map<UUID, Set<String>> failedVendors = new ConcurrentHashMap<>();

        // Track retry counts per paymentId
        private final Map<UUID, Integer> retryCount = new ConcurrentHashMap<>();

        @Timed(value = "service.execution", extraTags = { "domain", "payment", "service", "PaymentService", "method",
                        "processPayment" })
        @Counted(value = "service.execution.count", extraTags = { "domain", "payment", "service", "PaymentService",
                        "method", "processPayment" })
        public PaymentResponse processPayment(PaymentRequest request, UUID userId) {
                UUID paymentId = request.getPaymentId();

                log.info("Processing payment - userId: {}, merchantId: {}, paymentId: {}",
                                userId, request.getMerchant().getMerchantId(), paymentId);

                // Increment retry count
                retryCount.merge(paymentId, 1, Integer::sum);

                // IDEMPOTENCY: Check if payment already processed for this paymentId
                if (paymentCache.containsKey(paymentId)) {
                        Transaction existingTxn = paymentCache.get(paymentId);

                        // If payment was successful, return the cached result
                        if ("SUCCESS".equals(existingTxn.getStatus())) {
                                log.info("Payment already successful for paymentId: {}, txnId: {}",
                                                paymentId, existingTxn.getTxnId());
                                return buildResponse(paymentId, existingTxn);
                        }

                        // If payment failed, check if we should retry
                        if ("FAILED".equals(existingTxn.getStatus())) {
                                String failureReason = existingTxn.getFailureReason();

                                // Retry only for vendor/instrument failures, not for validation failures
                                if (isRetryableFailure(failureReason)) {
                                        log.info("Retrying failed payment - paymentId: {}, previousFailure: {}",
                                                        paymentId, failureReason);
                                        // Continue with retry logic below
                                } else {
                                        log.info("Payment failed with non-retryable error - paymentId: {}, reason: {}",
                                                        paymentId, failureReason);
                                        return buildResponse(paymentId, existingTxn);
                                }
                        }

                        // If payment is PENDING, return the pending status
                        if ("PENDING".equals(existingTxn.getStatus())) {
                                log.info("Payment still pending - paymentId: {}, txnId: {}",
                                                paymentId, existingTxn.getTxnId());
                                return buildResponse(paymentId, existingTxn);
                        }
                }

                // Smart Routing with vendor exclusion for retries
                List<VendorHealth> candidates = vendorAvailabilityService.getAvailableVendors();

                // Exclude vendors that have already failed for this payment
                Set<String> excludedVendors = failedVendors.getOrDefault(paymentId, new HashSet<>());
                List<VendorHealth> availableVendors = candidates.stream()
                                .filter(v -> !excludedVendors.contains(v.getVendorId()))
                                .collect(Collectors.toList());

                log.debug("Found {} available vendor candidates ({} excluded for retry)",
                                availableVendors.size(), excludedVendors.size());

                if (availableVendors.isEmpty()) {
                        Transaction failedTxn = createFailedTransaction(
                                        paymentId, userId, request,
                                        "No payment vendors available (all vendors exhausted)",
                                        FailureType.VENDOR_ERROR);
                        paymentCache.put(paymentId, failedTxn);
                        return buildResponse(paymentId, failedTxn);
                }

                // Sort by Uptime (DESC), Error Rate (ASC), Fee (ASC)
                VendorHealth selectedVendor = availableVendors.stream()
                                .sorted(Comparator.comparing(VendorHealth::getUptimeScore).reversed()
                                                .thenComparing(VendorHealth::getErrorRate)
                                                .thenComparing(v -> pricingService.calculateFee(v.getVendorId(),
                                                                request.getPayment().getAmount())))
                                .findFirst()
                                .orElseThrow();

                log.info("Selected vendor: {} for paymentId: {}", selectedVendor.getVendorId(), paymentId);

                // Initialize Transaction
                Transaction txn = new Transaction();
                txn.setTxnId(UUID.randomUUID());
                txn.setUserId(userId);
                txn.setMerchantId(request.getMerchant().getMerchantId());
                txn.setInstrumentId(request.getInstrument().getInstrumentId());
                txn.setMethodId(request.getInstrument().getMethodId());
                txn.setAmount(request.getPayment().getAmount());
                txn.setVendorId(selectedVendor.getVendorId());
                txn.setStatus("INITIATED");

                transactionRepository.save(txn);

                // Execute Payment (with vendor call)
                VendorExecutionResult result = executeVendorPayment(selectedVendor.getVendorId(), request);

                // Update Status based on vendor response
                if (result.isSuccess()) {
                        txn.setStatus("SUCCESS");
                        log.info("Payment successful - paymentId: {}, txnId: {}, vendor: {}",
                                        paymentId, txn.getTxnId(), selectedVendor.getVendorId());
                } else {
                        txn.setStatus("FAILED");
                        txn.setFailureReason(result.getFailureReason());

                        // Track failed vendor for this payment if vendor error
                        if (result.getFailureType() == FailureType.VENDOR_ERROR ||
                                        result.getFailureType() == FailureType.TIMEOUT) {
                                failedVendors.computeIfAbsent(paymentId, k -> new HashSet<>())
                                                .add(selectedVendor.getVendorId());
                        }

                        log.warn("Payment failed - paymentId: {}, txnId: {}, vendor: {}, type: {}, reason: {}",
                                        paymentId, txn.getTxnId(), selectedVendor.getVendorId(),
                                        result.getFailureType(), result.getFailureReason());
                }

                transactionRepository.save(txn);

                // Cache the transaction for idempotency
                paymentCache.put(paymentId, txn);

                return buildResponse(paymentId, txn, result.getFailureType());
        }

        @Timed(value = "service.execution", extraTags = { "domain", "payment", "service", "PaymentService", "method",
                        "getPaymentStatus" })
        @Counted(value = "service.execution.count", extraTags = { "domain", "payment", "service", "PaymentService",
                        "method", "getPaymentStatus" })
        public PaymentStatusResponse getPaymentStatus(UUID paymentId) {
                log.info("Querying payment status - paymentId: {}", paymentId);

                // Check cache first
                if (paymentCache.containsKey(paymentId)) {
                        Transaction txn = paymentCache.get(paymentId);
                        return buildStatusResponse(paymentId, txn);
                }

                // TODO: Query from database by paymentId when column is added
                // Optional<Transaction> txn = transactionRepository.findByPaymentId(paymentId);

                log.warn("Payment not found - paymentId: {}", paymentId);
                PaymentStatusResponse response = new PaymentStatusResponse();
                response.setPaymentId(paymentId);
                response.setStatus(PaymentStatusResponse.StatusEnum.NOT_FOUND);
                response.setRetryCount(0);
                return response;
        }

        private boolean isRetryableFailure(String failureReason) {
                if (failureReason == null) {
                        return false;
                }

                // Retryable failures: vendor issues, timeouts, instrument temporary issues
                return failureReason.contains("Vendor") ||
                                failureReason.contains("timeout") ||
                                failureReason.contains("temporarily unavailable") ||
                                failureReason.contains("network error");
        }

        private Transaction createFailedTransaction(
                        UUID paymentId, UUID userId, PaymentRequest request,
                        String failureReason, FailureType failureType) {

                Transaction txn = new Transaction();
                txn.setTxnId(UUID.randomUUID());
                txn.setUserId(userId);
                txn.setMerchantId(request.getMerchant().getMerchantId());
                txn.setInstrumentId(request.getInstrument().getInstrumentId());
                txn.setMethodId(request.getInstrument().getMethodId());
                txn.setAmount(request.getPayment().getAmount());
                txn.setStatus("FAILED");
                txn.setFailureReason(failureReason);

                transactionRepository.save(txn);
                return txn;
        }

        private PaymentResponse buildResponse(UUID paymentId, Transaction txn) {
                return buildResponse(paymentId, txn, null);
        }

        private PaymentResponse buildResponse(UUID paymentId, Transaction txn, FailureType failureType) {
                PaymentResponse response = new PaymentResponse();
                response.setPaymentId(paymentId);
                response.setTxnId(txn.getTxnId());
                response.setStatus(PaymentResponse.StatusEnum.fromValue(txn.getStatus()));
                response.setFailureReason(txn.getFailureReason());

                // Set retry flags based on failure type
                if ("FAILED".equals(txn.getStatus()) && failureType != null) {
                        response.setRetryable(failureType == FailureType.VENDOR_ERROR ||
                                        failureType == FailureType.TIMEOUT);
                        response.setRequiresNewInstrument(failureType == FailureType.INSTRUMENT_DECLINE);
                } else {
                        response.setRetryable(false);
                        response.setRequiresNewInstrument(false);
                }

                return response;
        }

        private PaymentStatusResponse buildStatusResponse(UUID paymentId, Transaction txn) {
                PaymentStatusResponse response = new PaymentStatusResponse();
                response.setPaymentId(paymentId);
                response.setTxnId(txn.getTxnId());
                response.setStatus(PaymentStatusResponse.StatusEnum.fromValue(txn.getStatus()));
                response.setAmount(txn.getAmount());
                response.setFailureReason(txn.getFailureReason());
                response.setRetryCount(retryCount.getOrDefault(paymentId, 0));

                // Convert LocalDateTime to OffsetDateTime
                if (txn.getCreatedAt() != null) {
                        response.setCreatedAt(txn.getCreatedAt().atZone(ZoneId.systemDefault()));
                }
                if (txn.getUpdatedAt() != null) {
                        response.setUpdatedAt(txn.getUpdatedAt().atZone(ZoneId.systemDefault()));
                }
                return response;
        }

        private VendorExecutionResult executeVendorPayment(String vendorId, PaymentRequest request) {
                // Mock execution - in real life, this calls PayU/Razorpay API
                // Simulate different vendor behaviors

                double random = Math.random();

                // Simulate 5% vendor failure rate
                if (random < 0.05) {
                        return VendorExecutionResult.failure(
                                        "Vendor processing failed - temporary issue",
                                        FailureType.VENDOR_ERROR);
                }

                // Simulate 3% instrument decline rate
                if (random < 0.08) {
                        return VendorExecutionResult.failure(
                                        "Card declined - Insufficient funds",
                                        FailureType.INSTRUMENT_DECLINE);
                }

                // Simulate 2% timeout
                if (random < 0.10) {
                        return VendorExecutionResult.failure(
                                        "Vendor timeout - network error",
                                        FailureType.TIMEOUT);
                }

                return VendorExecutionResult.success();
        }

        // Failure type enum
        private enum FailureType {
                VENDOR_ERROR, // Retryable with different vendor
                INSTRUMENT_DECLINE, // Requires new instrument
                VALIDATION_ERROR, // Non-retryable
                TIMEOUT // Retryable with same or different vendor
        }

        // Inner class for vendor execution result
        private static class VendorExecutionResult {
                private final boolean success;
                private final String failureReason;
                private final FailureType failureType;

                private VendorExecutionResult(boolean success, String failureReason, FailureType failureType) {
                        this.success = success;
                        this.failureReason = failureReason;
                        this.failureType = failureType;
                }

                public static VendorExecutionResult success() {
                        return new VendorExecutionResult(true, null, null);
                }

                public static VendorExecutionResult failure(String reason, FailureType type) {
                        return new VendorExecutionResult(false, reason, type);
                }

                public boolean isSuccess() {
                        return success;
                }

                public String getFailureReason() {
                        return failureReason;
                }

                public FailureType getFailureType() {
                        return failureType;
                }
        }
}
