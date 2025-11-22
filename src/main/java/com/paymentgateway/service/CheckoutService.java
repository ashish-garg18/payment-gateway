package com.paymentgateway.service;

import com.paymentgateway.generated.model.CheckoutResponse;
import com.paymentgateway.generated.model.InstrumentDetails;
import com.paymentgateway.generated.model.PaymentMethodOption;
import com.paymentgateway.model.PaymentInstrument;
import com.paymentgateway.model.PaymentMethod;
import com.paymentgateway.model.MerchantPaymentConfig;
import com.paymentgateway.repository.PaymentMethodRepository;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

        private final PaymentMethodRepository paymentMethodRepository;
        private final CustomerInstrumentService customerInstrumentService;
        private final MerchantConfigService merchantConfigService;
        private final RuleEngineService ruleEngineService;
        private final DowntimeService downtimeService;
        private final StringRedisTemplate redisTemplate;

        // In-memory cache for idempotency (TODO: Move to Redis)
        private final Map<UUID, CheckoutResponse> idempotencyCache = new ConcurrentHashMap<>();

        @Timed(value = "service.execution", extraTags = { "domain", "checkout", "service", "CheckoutService", "method",
                        "getCheckoutOptions" })
        @Counted(value = "service.execution.count", extraTags = { "domain", "checkout", "service", "CheckoutService",
                        "method", "getCheckoutOptions" })
        public CheckoutResponse getCheckoutOptions(
                        UUID checkoutId,
                        UUID idempotencyKey,
                        UUID merchantId,
                        UUID userId,
                        Double amount,
                        String mcc,
                        UUID paymentId) { // NEW - optional parameter for retry scenarios

                log.debug("Getting checkout options - checkoutId: {}, idempotencyKey: {}, merchantId: {}, userId: {}, paymentId: {}",
                                checkoutId, idempotencyKey, merchantId, userId, paymentId);

                // Check idempotency cache - if same request made before, return cached response
                if (idempotencyCache.containsKey(idempotencyKey)) {
                        CheckoutResponse cachedResponse = idempotencyCache.get(idempotencyKey);
                        log.info("Returning cached checkout - checkoutId: {}, paymentId: {}",
                                        checkoutId, cachedResponse.getPaymentId());
                        return cachedResponse;
                }

                // If paymentId provided (retry scenario), fetch declined instruments from Redis
                Set<String> declinedInstruments = new HashSet<>();
                if (paymentId != null) {
                        String key = "checkout:declined:" + paymentId;
                        Set<String> members = redisTemplate.opsForSet().members(key);
                        if (members != null) {
                                declinedInstruments.addAll(members);
                                log.debug("Retry scenario - paymentId: {}, declined instruments: {}", paymentId,
                                                declinedInstruments);
                        }
                }

                // Generate deterministic payment ID from idempotency key
                // This ensures same idempotency key always generates same payment ID
                UUID generatedPaymentId = UUID.nameUUIDFromBytes(idempotencyKey.toString().getBytes());

                // 1. Load Global Methods
                List<PaymentMethod> globalMethods = paymentMethodRepository.findByActiveTrue();
                log.debug("Loaded {} global payment methods for checkoutId: {}", globalMethods.size(), checkoutId);

                // 2. Fetch User Instruments
                List<PaymentInstrument> userInstruments = customerInstrumentService.getInstrumentsForUser(userId);
                log.debug("Found {} user instruments for userId: {}, checkoutId: {}",
                                userInstruments.size(), userId, checkoutId);

                List<PaymentMethodOption> methodOptions = new ArrayList<>();

                for (PaymentMethod method : globalMethods) {
                        // Get Merchant Config for this method
                        MerchantPaymentConfig merchantConfig = merchantConfigService.getConfig(merchantId,
                                        method.getMethodId());

                        // 3. Apply Rules for the Method itself
                        String methodIneligibilityReason = ruleEngineService.getIneligibilityReason(method, null,
                                        merchantId, mcc, amount);
                        boolean methodEnabled = methodIneligibilityReason == null;

                        // 4. Filter and Map User Instruments for this Method
                        List<InstrumentDetails> instrumentDetails = userInstruments.stream()
                                        .filter(instr -> instr.getMethodId().equals(method.getMethodId()))
                                        .map(instr -> {
                                                String instrRuleReason;
                                                boolean isDown;

                                                // Optimization: If method is already ineligible, skip
                                                // instrument-specific checks
                                                if (!methodEnabled) {
                                                        instrRuleReason = methodIneligibilityReason;
                                                        isDown = false;
                                                } else {
                                                        // Check Instrument specific rules
                                                        instrRuleReason = ruleEngineService.getIneligibilityReason(
                                                                        method, instr, merchantId, mcc, amount);
                                                        // Check Downtime
                                                        isDown = downtimeService.isInstrumentDown(method.getMethodId(),
                                                                        instr.getIssuer());
                                                }

                                                boolean eligible = instrRuleReason == null && !isDown;
                                                String reason = instrRuleReason;
                                                if (reason == null && isDown) {
                                                        reason = "Instrument detected as Down";
                                                }

                                                InstrumentDetails details = new InstrumentDetails();
                                                details.setInstrumentId(instr.getInstrumentId());
                                                details.setMethodId(method.getMethodId());
                                                details.setMaskedDetails(instr.getMaskedDetails());
                                                details.setNetwork(instr.getNetwork());
                                                details.setIssuer(instr.getIssuer());
                                                details.setType(method.getMethodId());
                                                details.setEligible(eligible);
                                                details.setIneligibilityReason(reason);
                                                return details;
                                        })
                                        .collect(Collectors.toList());

                        PaymentMethodOption option = new PaymentMethodOption();
                        option.setMethodId(method.getMethodId());
                        option.setMethodName(method.getMethodName());
                        option.setSupportedNetworks(method.getSupportedNetworks());
                        option.setEnabled(methodEnabled);
                        option.setReasonIfDisabled(methodIneligibilityReason);
                        option.setAllowAddNew(methodEnabled);
                        option.setUserInstruments(instrumentDetails);

                        methodOptions.add(option);
                }

                CheckoutResponse response = new CheckoutResponse();
                response.setPaymentId(generatedPaymentId); // Deterministic payment ID from idempotency key
                response.setPaymentMethods(methodOptions);

                // Cache the response for idempotency
                idempotencyCache.put(idempotencyKey, response);

                log.info("Checkout session created - checkoutId: {}, paymentId: {}, methods: {}",
                                checkoutId, generatedPaymentId, methodOptions.size());

                return response;
        }

        public void addDeclinedInstrument(UUID paymentId, UUID instrumentId) {
                if (paymentId == null || instrumentId == null) {
                        return;
                }
                String key = "checkout:declined:" + paymentId;
                redisTemplate.opsForSet().add(key, instrumentId.toString());
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
                log.info("Added declined instrument to Redis - paymentId: {}, instrumentId: {}", paymentId,
                                instrumentId);
        }
}
