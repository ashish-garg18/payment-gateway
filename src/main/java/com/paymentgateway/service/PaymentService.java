package com.paymentgateway.service;

import com.paymentgateway.generated.model.PaymentRequest;
import com.paymentgateway.generated.model.PaymentResponse;
import com.paymentgateway.generated.model.PaymentStatusResponse;
import com.paymentgateway.model.Transaction;
import com.paymentgateway.model.VendorHealth;
import com.paymentgateway.repository.TransactionRepository;
import com.paymentgateway.service.impl.VendorExecutionResult;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
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
        private final MerchantConfigService merchantConfigService;
        private final CheckoutService checkoutService;
        private final StringRedisTemplate redisTemplate;
        private final ObjectMapper objectMapper;
        private final VendorExecutionService vendorExecutionService;

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
                String retryKey = "payment:retry:" + paymentId;
                redisTemplate.opsForValue().increment(retryKey);
                redisTemplate.expire(retryKey, 24, TimeUnit.HOURS);

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
                                        VendorExecutionResult.FailureType.VENDOR_ERROR);
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
                txn.setPaymentId(paymentId);
                txn.setInstrumentId(request.getInstrument().getInstrumentId());
                txn.setMethodId(request.getInstrument().getMethodId());
                txn.setAmount(request.getPayment().getAmount());
                txn.setVendorId(selectedVendor.getVendorId());
                txn.setStatus("INITIATED");
                txn.setCreatedAt(LocalDateTime.now());

                transactionRepository.save(txn);

                // Execute Payment (with vendor call)
                VendorExecutionResult result = vendorExecutionService.executeVendorPayment(selectedVendor.getVendorId(),
                                request);

                // Update Status based on vendor response
                if (result.isSuccess()) {
                        txn.setStatus("SUCCESS");
                        txn.setUpdatedAt(LocalDateTime.now());
                        transactionRepository.save(txn);
                        paymentCache.put(paymentId, txn);
                        savePaymentStatus(paymentId, txn, 0);
                        return buildResponse(paymentId, txn);
                } else {
                        txn.setStatus("FAILED");
                        txn.setFailureReason(result.getFailureReason());
                        txn.setUpdatedAt(LocalDateTime.now());
                        transactionRepository.save(txn);
                        paymentCache.put(paymentId, txn);

                        // Track failed vendor
                        failedVendors.computeIfAbsent(paymentId, k -> new HashSet<>())
                                        .add(selectedVendor.getVendorId());

                        // Track retry count
                        int retries = retryCount.getOrDefault(paymentId, 0);
                        savePaymentStatus(paymentId, txn, retries);

                        return buildResponse(paymentId, txn, result.getFailureType());
                }
        }

        @Timed(value = "service.execution", extraTags = { "domain", "payment", "service", "PaymentService", "method",
                        "getPaymentStatus" })
        @Counted(value = "service.execution.count", extraTags = { "domain", "payment", "service", "PaymentService",
                        "method", "getPaymentStatus" })
        public PaymentStatusResponse getPaymentStatus(UUID paymentId) {
                log.info("Querying payment status - paymentId: {}", paymentId);

                // Check Redis first
                try {
                        String key = "payment:status:" + paymentId;
                        String statusJson = redisTemplate.opsForValue().get(key);
                        if (statusJson != null) {
                                return objectMapper.readValue(statusJson, PaymentStatusResponse.class);
                        }
                } catch (Exception e) {
                        log.error("Failed to deserialize payment status", e);
                }

                // Check in-memory cache (fallback)
                if (paymentCache.containsKey(paymentId)) {
                        Transaction txn = paymentCache.get(paymentId);
                        return buildStatusResponse(paymentId, txn);
                }

                log.warn("Payment not found - paymentId: {}", paymentId);
                PaymentStatusResponse response = new PaymentStatusResponse();
                response.setPaymentId(paymentId);
                response.setStatus(PaymentStatusResponse.StatusEnum.NOT_FOUND);
                response.setRetryCount(0);
                return response;
        }

        private void savePaymentStatus(UUID paymentId, Transaction txn, int retryCount) {
                try {
                        PaymentStatusResponse response = buildStatusResponse(paymentId, txn);
                        response.setRetryCount(retryCount);

                        String statusJson = objectMapper.writeValueAsString(response);
                        String key = "payment:status:" + paymentId;
                        redisTemplate.opsForValue().set(key, statusJson, 24, TimeUnit.HOURS);
                } catch (Exception e) {
                        log.error("Failed to save payment status to Redis", e);
                }
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
                        String failureReason, VendorExecutionResult.FailureType failureType) {

                Transaction txn = new Transaction();
                txn.setTxnId(UUID.randomUUID());
                txn.setUserId(userId);
                txn.setMerchantId(request.getMerchant().getMerchantId());
                txn.setPaymentId(paymentId);
                txn.setInstrumentId(request.getInstrument().getInstrumentId());
                txn.setMethodId(request.getInstrument().getMethodId());
                txn.setAmount(request.getPayment().getAmount());
                txn.setStatus("FAILED");
                txn.setFailureReason(failureReason);
                txn.setCreatedAt(LocalDateTime.now());

                transactionRepository.save(txn);
                return txn;
        }

        private PaymentResponse buildResponse(UUID paymentId, Transaction txn) {
                return buildResponse(paymentId, txn, null);
        }

        private PaymentResponse buildResponse(UUID paymentId, Transaction txn,
                        VendorExecutionResult.FailureType failureType) {
                PaymentResponse response = new PaymentResponse();
                response.setPaymentId(paymentId);
                response.setTxnId(txn.getTxnId());
                response.setStatus(PaymentResponse.StatusEnum.fromValue(txn.getStatus()));
                response.setFailureReason(txn.getFailureReason());

                // Set retry flags based on failure type
                if ("FAILED".equals(txn.getStatus()) && failureType != null) {
                        response.setRetryable(failureType == VendorExecutionResult.FailureType.VENDOR_ERROR ||
                                        failureType == VendorExecutionResult.FailureType.TIMEOUT);
                        response.setRequiresNewInstrument(
                                        failureType == VendorExecutionResult.FailureType.INSTRUMENT_DECLINE);
                } else {
                        response.setRetryable(false);
                        response.setRequiresNewInstrument(false);
                }

                // Set response code
                if (failureType != null) {
                        response.setResponseCode(getResponseCode(failureType));
                } else if ("SUCCESS".equals(txn.getStatus())) {
                        response.setResponseCode("00");
                } else {
                        response.setResponseCode("99");
                }

                return response;
        }

        private String getResponseCode(VendorExecutionResult.FailureType failureType) {
                if (failureType == null)
                        return "99";
                switch (failureType) {
                        case VENDOR_ERROR:
                                return "50";
                        case INSTRUMENT_DECLINE:
                                return "51";
                        case VALIDATION_ERROR:
                                return "40";
                        case TIMEOUT:
                                return "54";
                        default:
                                return "99";
                }
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
}
