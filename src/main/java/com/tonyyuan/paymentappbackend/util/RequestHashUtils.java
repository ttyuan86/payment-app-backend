package com.tonyyuan.paymentappbackend.util;

import com.tonyyuan.paymentappbackend.dto.PaymentCreateRequest;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for generating a deterministic SHA-256 hash of a PaymentCreateRequest.
 *
 * Purpose:
 *  - Used in the IdempotencyKey table to ensure that the same idempotency key
 *    is always tied to the same request payload.
 *  - Prevents accidental or malicious reuse of the same Idempotency-Key header
 *    for different payment details.
 *
 * How:
 *  - Extracts the most important, request-identifying fields.
 *  - Concatenates them in a fixed order with '|' as a separator.
 *  - Sorts invoiceIds before joining, so order differences don’t affect the hash.
 *  - Produces a SHA-256 digest of the normalized string.
 *
 * ⚠️ Important:
 *  - Includes sensitive fields like PAN and CVV → never log the normalized string!
 *  - Hash is only for equality check, not reversible (one-way).
 */
public final class RequestHashUtils {

    private RequestHashUtils() {
        // Utility class: prevent instantiation
    }

    /**
     * Compute a SHA-256 hash of the normalized request payload.
     *
     * @param r the incoming PaymentCreateRequest
     * @return byte[] of the SHA-256 digest
     */
    public static byte[] hash(PaymentCreateRequest r) {
        // Defensive copy + sort to make invoiceIds order-independent
        List<String> invoices = new ArrayList<>(r.getInvoiceIds());
        Collections.sort(invoices);

        // Build canonical string
        String normalized = new StringBuilder()
                .append(r.getFirstName()).append('|')
                .append(r.getLastName()).append('|')
                .append(r.getCardNumber()).append('|')
                .append(r.getExpiryMonth()).append('|')
                .append(r.getExpiryYear()).append('|')
                .append(r.getCvv()).append('|')
                .append(r.getAmountMinor()).append('|')
                .append(r.getCurrency()).append('|')
                .append(String.join(",", invoices))
                .toString();

        // Compute SHA-256 digest
        return DigestUtils.sha256(normalized.getBytes(StandardCharsets.UTF_8));
    }
}
