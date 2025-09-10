package com.tonyyuan.paymentappbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import com.tonyyuan.paymentappbackend.service.PaymentService;
import com.tonyyuan.paymentappbackend.config.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link PaymentController}.
 *
 * These tests use MockMvc to simulate HTTP requests against the API layer,
 * but mock out dependencies like PaymentService and RateLimiterService
 * to avoid actual database writes or external calls.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // disable Spring Security filters for testing
@ActiveProfiles("test") // use test profile (e.g., in-memory DB if configured)
class PaymentControllerIT {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;

    @MockBean private PaymentService paymentService;   // Mocked to avoid persisting to DB
    @MockBean private RateLimiterService rateLimiter;  // Mocked to simulate rate limiting

    /**
     * Utility method to generate a valid PaymentCreateRequest
     * with a future expiry date and realistic data.
     */
    private PaymentCreateRequest validReq() {
        YearMonth next = YearMonth.now().plusYears(1);
        PaymentCreateRequest r = new PaymentCreateRequest();
        r.setFirstName("Tony");
        r.setLastName("Yuan");
        r.setCardNumber("4111111111111111");
        r.setExpiryMonth(next.getMonthValue());
        r.setExpiryYear(next.getYear());
        r.setCvv("123");
        r.setAmountMinor(1299);
        r.setCurrency("AUD");
        r.setClientReferenceId("order-001");
        r.setInvoiceIds(Collections.singletonList("INV-1"));
        return r;
    }

    /**
     * Test: Successful payment creation.
     * Expectation:
     *  - Returns 201 Created
     *  - JSON contains "status": "SUCCEEDED"
     *  - Masked card number contains "******"
     */
    @Test
    void postPayments_success_should201() throws Exception {
        Mockito.when(rateLimiter.tryConsume()).thenReturn(true);
        Mockito.when(paymentService.create(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(inv -> new com.tonyyuan.paymentappbackend.dto.PaymentResponse(
                        "pay_1234567890ab",
                        "SUCCEEDED",
                        1299,
                        "AUD",
                        "411111******1111",
                        "VISA",
                        12,
                        2030,
                        "tenant_demo",
                        "idem-123"
                ));

        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-Id", "tenant_demo")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(om.writeValueAsString(validReq())))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.maskedCard").value(containsString("******")));
    }

    /**
     * Test: Missing required headers (X-Client-Id, Idempotency-Key).
     * Expectation:
     *  - Returns 400 Bad Request
     */
    @Test
    void postPayments_missingHeader_should400() throws Exception {
        Mockito.when(rateLimiter.tryConsume()).thenReturn(true);

        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(validReq())))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test: Request is rate-limited.
     * Expectation:
     *  - Returns 429 Too Many Requests
     *  - JSON contains error = "Too Many Requests"
     */
    @Test
    void postPayments_rateLimited_should429() throws Exception {
        Mockito.when(rateLimiter.tryConsume()).thenReturn(false);

        mvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-Id", "tenant_demo")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(om.writeValueAsString(validReq())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    /**
     * Test: Preflight CORS request.
     * Expectation:
     *  - Returns 200 OK
     *  - Includes CORS headers allowing requests from http://localhost:5173
     */
    @Test
    void optionsPreflight_shouldReturnCorsHeaders() throws Exception {
        mvc.perform(options("/api/v1/payments")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
