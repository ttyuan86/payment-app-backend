package com.tonyyuan.paymentappbackend.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.OffsetDateTime;

/**
 * Entity representing an idempotency key record.
 *
 * This table ensures that repeated requests with the same (tenantId + idempotencyKey)
 * are handled in an idempotent manner — either returning the same response or rejecting duplicates.
 */
@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_ik",
                        columnNames = {"tenantId", "idempotencyKey"}
                )
        }
)
@Getter
@Setter
public class IdempotencyKey {

    /** Auto-incremented primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant or client identifier, required for multi-tenant separation. */
    @Column(nullable = false)
    private String tenantId;

    /** Unique idempotency key provided by the client for safe retries. */
    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    /**
     * Hash of the request payload to detect if the same idempotency key
     * is being used for a different request body (which is invalid).
     */
    @Column(nullable = true)
    private byte[] requestHash;

    /**
     * Cached JSON response associated with this idempotency key.
     * Allows quick replay of the original response to duplicate requests.
     */
    @Lob
    private String responseJson;

    /**
     * Current status of the key:
     * - PENDING: request in progress
     * - COMPLETED: request finished successfully
     * - FAILED: request failed and should not be retried
     */
    @Column(nullable = false, length = 16)
    private String status;

    /** Expiration time for this key — expired keys may be cleaned up. */
    private OffsetDateTime expiresAt;

    /** Creation timestamp (default to now). */
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
