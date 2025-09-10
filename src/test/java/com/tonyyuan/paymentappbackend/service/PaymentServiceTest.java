package com.tonyyuan.paymentappbackend.service;

import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import com.tonyyuan.paymentappbackend.dto.PaymentResponse;
import com.tonyyuan.paymentappbackend.entity.Payment;
import com.tonyyuan.paymentappbackend.entity.PaymentInvoice;
import com.tonyyuan.paymentappbackend.repository.IdempotencyKeyRepository;
import com.tonyyuan.paymentappbackend.repository.PaymentInvoiceRepository;
import com.tonyyuan.paymentappbackend.repository.PaymentRepository;
import com.tonyyuan.paymentappbackend.util.AesGcmEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * Pure Mockito-based tests (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepo;
    @Mock private PaymentInvoiceRepository paymentInvoiceRepo;
    @Mock private IdempotencyService idemSvc;
    @Mock private AesGcmEncryptor encryptor;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepo;
    @Mock private EntityManager entityManager;
    @InjectMocks
    private PaymentService paymentService;

    private PaymentCreateRequest baseReq;

    @BeforeEach
    void setUp() {
        baseReq = validReq();
    }

    // ============ Happy Path ============

    @Test
    void create_success() {
        String tenantId = "tenantA";
        String idemKey  = "idem-1";
        String idemMapKey = tenantId + ":" + idemKey;

        // Idempotency flow: allow creation
        when(idemSvc.isCompleted(idemMapKey)).thenReturn(false);
        when(idemSvc.isPending(idemMapKey)).thenReturn(false);
        when(idemSvc.tryAcquire(idemMapKey)).thenReturn(true);

        // Deterministic encryptor output for assertions
        byte[] ct  = new byte[]{1,2,3};
        byte[] iv  = new byte[12];
        byte[] tag = new byte[16];
        Arrays.fill(iv, (byte)7);
        Arrays.fill(tag, (byte)8);
        when(encryptor.encryptPan(eq(baseReq.getCardNumber()), any(byte[].class)))
                .thenReturn(new AesGcmEncryptor.Result(ct, iv, tag, "demo-v1"));

        // saveAndFlush returns the same entity
        when(paymentRepo.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse resp = paymentService.create(tenantId, idemKey, baseReq);

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(resp.getAmountMinor()).isEqualTo(baseReq.getAmountMinor());
        assertThat(resp.getCurrency()).isEqualTo(baseReq.getCurrency());
        assertThat(resp.getMaskedCard()).contains("******");
        assertThat(resp.getBrand()).isNotBlank();

        // Verify encrypted fields persisted
        ArgumentCaptor<Payment> cap = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).saveAndFlush(cap.capture());
        Payment saved = cap.getValue();
        assertThat(saved.getEncPan()).containsExactly(ct);
        assertThat(saved.getIv()).hasSize(12).containsExactly(iv);
        assertThat(saved.getTag()).hasSize(16).containsExactly(tag);
        assertThat(saved.getDekKid()).isEqualTo("demo-v1");

        // Invoice links saved & flushed
        verify(paymentInvoiceRepo, times(baseReq.getInvoiceIds().size()))
                .save(any(PaymentInvoice.class));
        verify(paymentInvoiceRepo).flush();

        // Idempotency state transitions
        verify(idemSvc).markPending(idemMapKey);
        verify(idemSvc).markCompleted(idemMapKey);
        verify(idemSvc).release(idemMapKey);
    }

    // ============ Validation Errors ============

    @Test
    void create_invalidCardNumber_should400() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;
        when(idemSvc.isCompleted(mapKey)).thenReturn(false);
        when(idemSvc.isPending(mapKey)).thenReturn(false);
        when(idemSvc.tryAcquire(mapKey)).thenReturn(true);

        PaymentCreateRequest req = validReq();
        req.setCardNumber("1234567890"); // Luhn fails

        assertThatThrownBy(() -> paymentService.create(tenant, key, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(paymentRepo, never()).saveAndFlush(any());
        verify(paymentInvoiceRepo, never()).save(any());
        verify(idemSvc).release(mapKey);
    }

    @Test
    void create_expired_should400() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;
        when(idemSvc.isCompleted(mapKey)).thenReturn(false);
        when(idemSvc.isPending(mapKey)).thenReturn(false);
        when(idemSvc.tryAcquire(mapKey)).thenReturn(true);

        PaymentCreateRequest req = validReq();
        YearMonth last = YearMonth.now().minusMonths(1);
        req.setExpiryMonth(last.getMonthValue());
        req.setExpiryYear(last.getYear());

        assertThatThrownBy(() -> paymentService.create(tenant, key, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(idemSvc).release(mapKey);
    }

    @Test
    void create_amountNonPositive_should400() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;
        when(idemSvc.isCompleted(mapKey)).thenReturn(false);
        when(idemSvc.isPending(mapKey)).thenReturn(false);
        when(idemSvc.tryAcquire(mapKey)).thenReturn(true);

        PaymentCreateRequest req = validReq();
        req.setAmountMinor(0);

        assertThatThrownBy(() -> paymentService.create(tenant, key, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(idemSvc).release(mapKey);
    }

    // ============ Invoice Conflict ============

    @Test
    void create_duplicateInvoice_should409() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;
        when(idemSvc.isCompleted(mapKey)).thenReturn(false);
        when(idemSvc.isPending(mapKey)).thenReturn(false);
        when(idemSvc.tryAcquire(mapKey)).thenReturn(true);

        when(encryptor.encryptPan(anyString(), any()))
                .thenReturn(new AesGcmEncryptor.Result(new byte[]{1}, new byte[12], new byte[16], "kid"));
        when(paymentRepo.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Simulate unique-constraint violation on invoice link flush
        doThrow(new DataIntegrityViolationException("duplicate invoice"))
                .when(paymentInvoiceRepo).flush();

        assertThatThrownBy(() -> paymentService.create(tenant, key, validReq()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(idemSvc).release(mapKey);
    }

    // ============ Idempotency Fast Paths ============

    @Test
    void idempotency_completed_shouldReturnExisting() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;

        when(idemSvc.isCompleted(mapKey)).thenReturn(true);

        Payment existing = new Payment();
        existing.setPaymentId("pay_exist");
        existing.setStatus("SUCCEEDED");
        existing.setAmountMinor(500L);
        existing.setCurrency("AUD");
        existing.setMaskedCard("411111******1111");
        existing.setBrand("VISA");
        existing.setExpiryMonth(12);
        existing.setExpiryYear(2030);
        existing.setTenantId(tenant);
        existing.setIdempotencyKey(key);

        when(paymentRepo.findByTenantIdAndIdempotencyKey(tenant, key))
                .thenReturn(Optional.of(existing));

        PaymentResponse resp = paymentService.create(tenant, key, validReq());
        assertThat(resp.getPaymentId()).isEqualTo("pay_exist");

        verify(paymentRepo, never()).saveAndFlush(any());
        verify(idemSvc, never()).tryAcquire(anyString());
    }

    @Test
    void idempotency_pending_should202() {
        String tenant = "t1"; String key = "k1";
        String mapKey = tenant + ":" + key;
        when(idemSvc.isCompleted(mapKey)).thenReturn(false);
        when(idemSvc.isPending(mapKey)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.create(tenant, key, validReq()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatus())
                        .isEqualTo(HttpStatus.ACCEPTED));

        verify(paymentRepo, never()).saveAndFlush(any());
    }

    // ============ Concurrency (only one actual save) ============

    /**
     * Uses a stateful FakeIdemSvc to ensure only the first thread acquires the lock
     * and triggers the actual save.
     */
    @Test
    void create_concurrent_sameIdem_onlyOneSucceeds() throws Exception {
        IdempotencyService statefulIdem = new FakeIdemSvc();
        PaymentService svc = new PaymentService(
                paymentRepo,
                paymentInvoiceRepo,
                statefulIdem,
                idempotencyKeyRepo,
                entityManager,
                encryptor
        );
        when(encryptor.encryptPan(anyString(), any()))
                .thenReturn(new AesGcmEncryptor.Result(new byte[]{1}, new byte[12], new byte[16], "kid"));
        when(paymentRepo.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String tenant = "t-con";
        String idem   = "same-key";
        PaymentCreateRequest req = validReq();

        int N = 6;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            futures.add(pool.submit(() -> {
                try {
                    svc.create(tenant, idem, req);
                } catch (ResponseStatusException ex) {
                    // For concurrent callers, ACCEPTED(202) is expected; rethrow others
                    if (ex.getStatus() != HttpStatus.ACCEPTED) throw ex;
                }
            }));
        }
        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one actual DB save
        verify(paymentRepo, times(1)).saveAndFlush(any(Payment.class));
    }

    // ---------- Helpers ----------

    private PaymentCreateRequest validReq() {
        PaymentCreateRequest r = new PaymentCreateRequest();
        r.setFirstName("Tony");
        r.setLastName("Yuan");
        r.setCardNumber("4111111111111111"); // Luhn ok
        YearMonth next = YearMonth.now().plusYears(1);
        r.setExpiryMonth(next.getMonthValue());
        r.setExpiryYear(next.getYear());
        r.setCvv("123");
        r.setAmountMinor(1299);
        r.setCurrency("AUD");
        r.setClientReferenceId("order-001");
        r.setInvoiceIds(Arrays.asList("INV-1", "INV-2"));
        return r;
    }

    /**
     * Minimal stateful IdempotencyService for concurrency test:
     *  - First tryAcquire(key) wins; others fail until completed.
     */
    static class FakeIdemSvc implements IdempotencyService {
        private final Set<String> completed = new ConcurrentSkipListSet<>();
        private final ConcurrentMap<String, Boolean> locks = new ConcurrentHashMap<>();

        @Override public boolean isCompleted(String key) { return completed.contains(key); }
        @Override public boolean isPending(String key) { return locks.getOrDefault(key, false) && !completed.contains(key); }
        @Override public boolean tryAcquire(String key) { return locks.putIfAbsent(key, true) == null; }
        @Override public void markPending(String key) { /* not needed for this test */ }
        @Override public void markCompleted(String key) { completed.add(key); locks.remove(key); }
        @Override public void release(String key) { /* no-op for test */ }
    }
}
