package com.paymentgateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.generated.model.*;
import com.paymentgateway.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
public class PaymentControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private PaymentService paymentService;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        public void testProcessPayment_Success() throws Exception {
                UUID userId = UUID.randomUUID();
                UUID paymentId = UUID.randomUUID();
                UUID txnId = UUID.randomUUID();

                PaymentRequest request = new PaymentRequest();
                request.setPaymentId(paymentId);

                MerchantDetails merchant = new MerchantDetails();
                merchant.setMerchantId(UUID.randomUUID());
                request.setMerchant(merchant);

                PaymentDetails payment = new PaymentDetails();
                payment.setAmount(100.0);
                request.setPayment(payment);

                PaymentInstrument instrument = new PaymentInstrument();
                instrument.setInstrumentId(UUID.randomUUID());
                instrument.setMethodId("CREDIT_CARD");
                request.setInstrument(instrument);

                PaymentResponse response = new PaymentResponse();
                response.setPaymentId(paymentId);
                response.setTxnId(txnId);
                response.setStatus(PaymentResponse.StatusEnum.SUCCESS);
                response.setRetryable(false);
                response.setRequiresNewInstrument(false);

                when(paymentService.processPayment(any(PaymentRequest.class), eq(userId)))
                                .thenReturn(response);

                mockMvc.perform(post("/payment/pay")
                                .header("X-User-Id", userId.toString())
                                .content(objectMapper.writeValueAsString(request))
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.retryable").value(false));
        }

        @Test
        public void testProcessPayment_RetryableFailure() throws Exception {
                UUID userId = UUID.randomUUID();
                UUID paymentId = UUID.randomUUID();

                PaymentRequest request = new PaymentRequest();
                request.setPaymentId(paymentId);
                // Add minimal required fields
                request.setMerchant(new MerchantDetails().merchantId(UUID.randomUUID()));
                request.setPayment(new PaymentDetails().amount(100.0));
                request.setInstrument(new PaymentInstrument().instrumentId(UUID.randomUUID()).methodId("UPI"));

                PaymentResponse response = new PaymentResponse();
                response.setPaymentId(paymentId);
                response.setStatus(PaymentResponse.StatusEnum.FAILED);
                response.setFailureReason("Vendor timeout");
                response.setRetryable(true);
                response.setRequiresNewInstrument(false);

                when(paymentService.processPayment(any(PaymentRequest.class), eq(userId)))
                                .thenReturn(response);

                mockMvc.perform(post("/payment/pay")
                                .header("X-User-Id", userId.toString())
                                .content(objectMapper.writeValueAsString(request))
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("FAILED"))
                                .andExpect(jsonPath("$.retryable").value(true));
        }

        @Test
        public void testGetPaymentStatus_Success() throws Exception {
                UUID paymentId = UUID.randomUUID();

                PaymentStatusResponse response = new PaymentStatusResponse();
                response.setPaymentId(paymentId);
                response.setStatus(PaymentStatusResponse.StatusEnum.SUCCESS);
                response.setRetryCount(1);

                when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);

                mockMvc.perform(get("/payment/status/{paymentId}", paymentId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.retryCount").value(1));
        }

        @Test
        public void testGetPaymentStatus_NotFound() throws Exception {
                UUID paymentId = UUID.randomUUID();

                PaymentStatusResponse response = new PaymentStatusResponse();
                response.setPaymentId(paymentId);
                response.setStatus(PaymentStatusResponse.StatusEnum.NOT_FOUND);

                when(paymentService.getPaymentStatus(paymentId)).thenReturn(response);

                mockMvc.perform(get("/payment/status/{paymentId}", paymentId))
                                .andExpect(status().isOk()) // Service returns 200 with NOT_FOUND status in body
                                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
        }
}
