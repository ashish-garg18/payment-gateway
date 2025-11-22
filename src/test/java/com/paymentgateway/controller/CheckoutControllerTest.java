package com.paymentgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.generated.model.CheckoutRequest;
import com.paymentgateway.generated.model.CheckoutResponse;
import com.paymentgateway.generated.model.MerchantDetails;
import com.paymentgateway.generated.model.PaymentDetails;
import com.paymentgateway.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutController.class)
public class CheckoutControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private CheckoutService checkoutService;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        public void testGetCheckoutOptions_Success() throws Exception {
                UUID merchantId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                UUID idempotencyKey = UUID.randomUUID();
                UUID checkoutId = UUID.randomUUID();
                UUID paymentId = UUID.randomUUID();

                CheckoutResponse mockResponse = new CheckoutResponse();
                mockResponse.setPaymentId(paymentId);
                mockResponse.setPaymentMethods(new ArrayList<>());

                CheckoutRequest request = new CheckoutRequest();
                request.setCheckoutId(checkoutId);

                MerchantDetails merchant = new MerchantDetails();
                merchant.setMerchantId(merchantId);
                merchant.setMcc("1234");
                request.setMerchant(merchant);

                PaymentDetails payment = new PaymentDetails();
                payment.setAmount(100.0);
                payment.setCurrency("INR");
                request.setPayment(payment);

                when(checkoutService.getCheckoutOptions(
                                eq(checkoutId),
                                eq(idempotencyKey),
                                eq(merchantId),
                                eq(userId),
                                eq(100.0),
                                eq("1234"),
                                isNull()))
                                .thenReturn(mockResponse);

                mockMvc.perform(post("/checkout") // Using correct path from spec
                                .header("X-User-Id", userId.toString())
                                .header("X-Idempotency-Key", idempotencyKey.toString())
                                .content(objectMapper.writeValueAsString(request))
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk());
        }

        @Test
        public void testGetCheckoutOptions_ValidationError() throws Exception {
                CheckoutRequest request = new CheckoutRequest();
                // Missing required fields

                mockMvc.perform(post("/checkout")
                                .header("X-User-Id", UUID.randomUUID().toString())
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .content(objectMapper.writeValueAsString(request))
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isBadRequest());
        }
}
