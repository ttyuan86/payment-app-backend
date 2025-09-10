package com.tonyyuan.paymentappbackend.service;

import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import com.tonyyuan.paymentappbackend.dto.PaymentResponse;
import com.tonyyuan.paymentappbackend.entity.IdempotencyKey;
import com.tonyyuan.paymentappbackend.entity.Payment;
import com.tonyyuan.paymentappbackend.entity.PaymentInvoice;
import com.tonyyuan.paymentappbackend.repository.IdempotencyKeyRepository;
import com.tonyyuan.paymentappbackend.repository.PaymentInvoiceRepository;
import com.tonyyuan.paymentappbackend.repository.PaymentRepository;
import com.tonyyuan.paymentappbackend.util.AesGcmEncryptor;
import com.tonyyuan.paymentappbackend.util.CardUtils;
import com.tonyyuan.paymentappbackend.util.RequestHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment domain service.
 *
 * Responsibilities:
 *  - Validates request (headers + business rules).
 *  - Applies **double idempotency guards**:
 *      1) Fast in-memory guard (coarse, per-instance).
 *      2) Database guard via IdempotencyKey row with request hash.
 *  - Encrypts PAN using AES-GCM; stores IV/tag/KID and only a masked PAN.
 *  - Persists Payment and links invoices (unique per tenant+invoice).
 *  - Returns a minimal, client-safe DTO (PaymentResponse).
 *
 * Notes:
 *  - "In-memory" guard avoids hot-spot duplicate work during a burst.
 *  - "DB" guard is the source of truth across instances/restarts.
 *  - All PAN handling must avoid logs; only masked PAN appears in logs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final PaymentInvoiceRepository paymentInvoiceRepo;
    private final IdempotencyService idemSvc;                // in-memory guard
    private final IdempotencyKeyRepository idRepo;           // DB guard
    private final EntityManager em;                          // used to clear persistence context on constraint error
    private final AesGcmEncryptor encryptor;

    /**
     * Create a payment with idempotency.
     * The order of steps is deliberate:
     *   1) header validation + in-memory quick paths
     *   2) request-level validation (no state changes yet)
     *   3) DB idempotency "register / occupy" with request hash (PENDING)
     *   4) real work (encrypt + persist payment + link invoices)
     *   5) DB idempotency mark COMPLETED and return response
     */
    @Transactional
    public PaymentResponse create(String tenantId, String idempotencyKey, PaymentCreateRequest req) {
        log.info("[PAYMENT] Start create. tenantId={}, idempotencyKey={}, invoices={}",
                tenantId, idempotencyKey, req.getInvoiceIds());

        // ---------- (0) Basic header validations ----------
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Client-Id (tenantId)");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key");
        }

        final String memKey = tenantId + ":" + idempotencyKey;

        // ---------- (1) In-memory idempotency quick paths ----------
        // If we already completed this key in the current instance, try to load and return.
        if (idemSvc.isCompleted(memKey)) {
            Optional<Payment> prior = paymentRepo.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (prior.isPresent()) return toResponse(prior.get());
            // completed flag without row means a race or restart—fall through to DB guard.
        }

        // If someone else is working on it in this instance, ask client to retry later.
        if (idemSvc.isPending(memKey)) {
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "Processing, please retry later");
        }

        // Try to acquire the in-memory lock for this key.
        if (!idemSvc.tryAcquire(memKey)) {
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "Processing, please retry later");
        }

        try {
            // ---------- (2) Request-level business validations (stateless) ----------
            // Do not touch DB before we know the request is valid.

            // PAN must pass Luhn.
            if (!CardUtils.luhn(req.getCardNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card number (Luhn)");
            }

            // Expiry must be in the future.
            YearMonth exp = YearMonth.of(req.getExpiryYear(), req.getExpiryMonth());
            if (exp.isBefore(YearMonth.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card expired");
            }

            // Amount must be positive.
            if (req.getAmountMinor() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amountMinor must be positive");
            }

            // ---------- (3) Database idempotency guard (PENDING row with request hash) ----------
            byte[] requestHash = RequestHashUtils.hash(req);

            IdempotencyKey ik = new IdempotencyKey();
            ik.setTenantId(tenantId);
            ik.setIdempotencyKey(idempotencyKey);
            ik.setRequestHash(requestHash);                 // MUST be non-null to satisfy NOT NULL
            ik.setStatus("PENDING");
            ik.setExpiresAt(OffsetDateTime.now().plusHours(24)); // optional TTL

            try {
                idRepo.saveAndFlush(ik); // try to "occupy" this key
            } catch (DataIntegrityViolationException dup) {
                // Another transaction already inserted a row for (tenantId, idempotencyKey)
                // Clear persistence context to avoid follow-up flush errors:
                em.clear();

                IdempotencyKey existing = idRepo.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                        .orElseThrow(() -> dup);

                // If the request hash differs, the same key is reused for a different payload -> 409
                if (!java.util.Arrays.equals(existing.getRequestHash(), requestHash)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Idempotency key reused with different request");
                }

                // If previously COMPLETED, return the existing payment response.
                if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
                    return paymentRepo.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                            .map(PaymentService::toResponse)
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Idempotency key completed but payment record is missing"
                            ));
                }

                // Otherwise treat it as still processing.
                throw new ResponseStatusException(HttpStatus.ACCEPTED, "Processing, please retry later");
            }

            // Mark in-memory as "pending" after DB guard is in place.
            idemSvc.markPending(memKey);

            // ---------- (4) Real work: encrypt PAN, persist payment, link invoices ----------
            String masked = CardUtils.masked(req.getCardNumber());
            String brand  = CardUtils.brand(req.getCardNumber());

            // Use tenantId + paymentId as AAD so ciphertext is bound to this context.
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            byte[] aad = (tenantId + "|" + paymentId).getBytes(StandardCharsets.UTF_8);

            AesGcmEncryptor.Result enc = encryptor.encryptPan(req.getCardNumber(), aad);

            Payment p = new Payment();
            p.setPaymentId(paymentId);
            p.setTenantId(tenantId);
            p.setIdempotencyKey(idempotencyKey);
            p.setStatus("SUCCEEDED"); // Demo: in real life set from gateway result
            p.setAmountMinor(req.getAmountMinor());
            p.setCurrency(req.getCurrency());
            p.setMaskedCard(masked);
            p.setBrand(brand);
            p.setExpiryMonth(req.getExpiryMonth());
            p.setExpiryYear(req.getExpiryYear());
            p.setClientReferenceId(req.getClientReferenceId());

            // Persist encryption artifacts (all NOT NULL)
            p.setEncPan(enc.ciphertext);
            p.setIv(enc.iv);
            p.setTag(enc.tag);
            p.setDekKid(enc.dekKid);

            // Timestamps
            OffsetDateTime now = OffsetDateTime.now();
            p.setCreatedAt(now);
            p.setUpdatedAt(now);

            // For lineage/debug (optional; you already have a deterministic request hash above)
            p.setRequestHash(sha256(
                    tenantId + "|" + idempotencyKey + "|" + req.getAmountMinor() + "|" + req.getCurrency()
                            + "|" + req.getExpiryMonth() + "|" + req.getExpiryYear()
            ));

            Payment saved = paymentRepo.saveAndFlush(p);

            // Link invoices (unique by tenant_id + invoice_id).
            try {
                for (String invId : req.getInvoiceIds()) {
                    PaymentInvoice link = new PaymentInvoice();
                    link.setTenantId(tenantId);
                    link.setInvoiceId(invId);
                    link.setPayment(saved);
                    paymentInvoiceRepo.save(link);
                }
                paymentInvoiceRepo.flush();
            } catch (DataIntegrityViolationException dupInv) {
                // If any invoice is already linked (i.e., paid), signal 409.
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Some invoices already paid (duplicate invoice): " + String.join(",", req.getInvoiceIds()),
                        dupInv
                );
            }

            // ---------- (5) Mark DB idempotency COMPLETED, in-memory completed, and return ----------
            ik.setStatus("COMPLETED");
            idRepo.save(ik);

            idemSvc.markCompleted(memKey);
            log.info("[PAYMENT] Completed. paymentId={}, tenantId={}", saved.getPaymentId(), tenantId);

            return toResponse(saved);

        } catch (ResponseStatusException ex) {
            log.error("[PAYMENT] Business error: status={}, reason={}", ex.getStatus(), ex.getReason());
            // Optionally: if we created an IdempotencyKey row earlier and failed, set status to FAILED.
            // We avoid doing that here because we don't have a direct reference in all code paths.
            throw ex;
        } catch (Exception e) {
            log.error("[PAYMENT] Unexpected error while creating payment", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment creation failed", e);
        } finally {
            // Always release the in-memory guard.
            idemSvc.release(memKey);
            log.debug("[PAYMENT] Released in-memory idempotency lock: {}", memKey);
        }
    }

    // ---------- Helpers ----------

    private static byte[] sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(),
                p.getStatus(),
                p.getAmountMinor(),
                p.getCurrency(),
                p.getMaskedCard(),
                p.getBrand(),
                p.getExpiryMonth(),
                p.getExpiryYear(),
                p.getTenantId(),
                p.getIdempotencyKey()
        );
    }
}
