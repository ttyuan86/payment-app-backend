package com.tonyyuan.paymentappbackend.service;

import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import com.tonyyuan.paymentappbackend.dto.PaymentResponse;
import com.tonyyuan.paymentappbackend.entity.Payment;
import com.tonyyuan.paymentappbackend.repository.PaymentRepository;
import com.tonyyuan.paymentappbackend.util.AesGcmEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PaymentService}.
 *
 * These tests boot a full Spring context with H2 and replace a few beans
 * (AesGcmEncryptor and IdempotencyService) with deterministic test doubles
 * so assertions are stable and independent of randomness or external systems.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIT {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepo;

    /**
     * Test-only bean overrides for stable, repeatable results.
     */
    @TestConfiguration
    static class TestOverrides {

        /**
         * Minimal in-memory idempotency implementation for tests.
         * Behavior:
         *  - tryAcquire(key): succeeds the first time for a key.
         *  - markCompleted(key): marks key as completed.
         *  - isCompleted(key): returns true after completion.
         *  - release(): no-op for tests.
         */
        @Bean
        @Primary
        public IdempotencyService testIdemSvc() {
            return new IdempotencyService() {
                private final java.util.concurrent.ConcurrentHashMap<String, Boolean> pending =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private final java.util.concurrent.ConcurrentHashMap<String, Boolean> completed =
                        new java.util.concurrent.ConcurrentHashMap<>();

                @Override public boolean isCompleted(String key) { return completed.getOrDefault(key, false); }
                @Override public boolean isPending(String key) { return pending.getOrDefault(key, false) && !isCompleted(key); }
                @Override public boolean tryAcquire(String key) { return pending.putIfAbsent(key, true) == null; }
                @Override public void markPending(String key) { pending.put(key, true); }
                @Override public void markCompleted(String key) { completed.put(key, true); pending.remove(key); }
                @Override public void release(String key) { /* no-op */ }
            };
        }

        /**
         * Deterministic AES-GCM encryptor for tests.
         * Produces fixed ciphertext/iv/tag so we can assert lengths/values
         * without flakiness caused by randomness.
         *
         * NOTE: We override the @Component AesGcmEncryptor in main code via @Primary.
         */
        @Bean
        @Primary
        public AesGcmEncryptor deterministicEncryptor() {
            return new AesGcmEncryptor() {
                @Override
                public Result encryptPan(String pan, byte[] aad) {
                    byte[] ct  = new byte[]{9, 9, 9};
                    byte[] iv  = new byte[12];
                    byte[] tag = new byte[16];
                    java.util.Arrays.fill(iv,  (byte) 7);
                    java.util.Arrays.fill(tag, (byte) 8);
                    return new Result(ct, iv, tag, "it-kid");
                }
            };
        }
    }

    /**
     * Happy-path: service persists a payment to H2 with encrypted PAN fields
     * and returns a successful response.
     */
    @Test
    @Transactional
    void create_persists_to_h2_success() {
        // Arrange
        PaymentCreateRequest req = validReq();
        String tenantId = "tenant-it";
        String idemKey  = UUID.randomUUID().toString();

        // Act
        PaymentResponse resp = paymentService.create(tenantId, idemKey, req);

        // Assert response
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(resp.getAmountMinor()).isEqualTo(req.getAmountMinor());
        assertThat(resp.getCurrency()).isEqualTo(req.getCurrency());
        assertThat(resp.getMaskedCard()).contains("******");
        assertThat(resp.getBrand()).isNotBlank();

        // Assert persistence via repository lookup by (tenantId, idempotencyKey)
        Optional<Payment> opt = paymentRepo.findByTenantIdAndIdempotencyKey(tenantId, idemKey);
        assertThat(opt).isPresent();

        Payment p = opt.get();
        assertThat(p.getEncPan()).isNotNull().isNotEmpty();
        assertThat(p.getIv()).isNotNull().hasSize(12);
        assertThat(p.getTag()).isNotNull().hasSize(16);
        assertThat(p.getDekKid()).isEqualTo("it-kid");
    }

    /**
     * Calling the service twice with the same idempotency key should return
     * the same payment (idempotent behavior) and not create duplicates.
     */
    @Test
    @Transactional
    void create_sameIdempotencyKey_returnsExisting_onSecondCall() {
        // Arrange
        PaymentCreateRequest req = validReq();
        String tenantId = "tenant-it-2";
        String idemKey  = "idem-same-key";

        // Act - first call persists
        PaymentResponse first = paymentService.create(tenantId, idemKey, req);
        // Act - second call should hit idempotency path and return existing
        PaymentResponse second = paymentService.create(tenantId, idemKey, req);

        // Assert same payment id returned
        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());

        // Assert only one record exists in DB for that (tenant, idempotencyKey)
        Optional<Payment> opt = paymentRepo.findByTenantIdAndIdempotencyKey(tenantId, idemKey);
        assertThat(opt).isPresent();
        assertThat(opt.get().getPaymentId()).isEqualTo(first.getPaymentId());
    }

    // ---------- Helpers ----------

    /** Builds a valid request: Luhn-passing PAN, future expiry, positive amount. */
    private PaymentCreateRequest validReq() {
        PaymentCreateRequest r = new PaymentCreateRequest();
        r.setFirstName("Tony");
        r.setLastName("Yuan");
        r.setCardNumber("4111111111111111"); // Visa test number (passes Luhn)
        YearMonth next = YearMonth.now().plusYears(1);
        r.setExpiryMonth(next.getMonthValue());
        r.setExpiryYear(next.getYear());
        r.setCvv("123");
        r.setAmountMinor(2599);
        r.setCurrency("AUD");
        r.setClientReferenceId("order-it-1");
        r.setInvoiceIds(Arrays.asList("INV-IT-1"));
        return r;
        // For multiple invoices: Arrays.asList("INV-1", "INV-2")
    }
}
