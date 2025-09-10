package com.tonyyuan.paymentappbackend.controller;

import com.tonyyuan.paymentappbackend.config.RateLimiterService;
import com.tonyyuan.paymentappbackend.dto.ErrorResponse;
import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import com.tonyyuan.paymentappbackend.dto.PaymentResponse;
import com.tonyyuan.paymentappbackend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * REST controller for handling payment operations.
 *
 * Endpoints:
 *  - POST /api/v1/payments
 *
 * Features:
 *  - Applies a simple global rate limiter (demo only).
 *  - Validates headers and request body using @Valid and @RequestHeader.
 *  - Delegates payment creation to PaymentService.
 *  - Returns structured error responses when rate limit is exceeded.
 *
 * Notes:
 *  - In production, rate limiting is typically enforced per user or tenant
 *    (e.g., based on API key or X-Client-Id), not globally.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Validated
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;
    private final RateLimiterService rateLimiterService;

    /**
     * Creates a new payment.
     *
     * @param clientId        Tenant/client identifier from the request header.
     * @param idempotencyKey  Unique key to ensure idempotency across retries.
     * @param request         Payment request payload (validated).
     * @return HTTP 201 with PaymentResponse if successful,
     *         HTTP 429 if rate limit exceeded,
     *         or HTTP 400/409 depending on validation and business rules.
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    // In a real system, rate limiting should be per user/tenant, not global.
    public ResponseEntity<?> createPayment(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request,
            javax.servlet.http.HttpServletRequest httpReq) {

        if (!rateLimiterService.tryConsume()) {
            ErrorResponse error = ErrorResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error("Too Many Requests")
                    .message("Too many requests - please try again later.")
                    .path(httpReq.getRequestURI())
                    .build();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
        }

        PaymentResponse resp = service.create(clientId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp);
    }

}
