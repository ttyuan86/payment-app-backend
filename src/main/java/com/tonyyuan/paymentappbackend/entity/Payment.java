package com.tonyyuan.paymentappbackend.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.OffsetDateTime;

/**
 * Entity representing a payment transaction.
 *
 * This table stores encrypted card data (AES-GCM) along with metadata needed for
 * idempotency and reconciliation. Sensitive data (PAN) is encrypted before persistence.
 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_pay_tenant_ik",
                        columnNames = {"tenantId", "idempotencyKey"}
                )
        }
)
@Getter
@Setter
public class Payment {

    /** Auto-incremented primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public-facing identifier (e.g., "pay_xxx"). Unique across all tenants. */
    @Column(nullable = false, unique = true, length = 40)
    private String paymentId;

    /** Tenant or client identifier. */
    @Column(nullable = false)
    private String tenantId;

    /** Payment amount in minor units (e.g., cents). */
    @Column(nullable = false)
    private Long amountMinor;

    /** ISO 4217 currency code (e.g., "USD", "AUD"). */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Payment status (e.g., "SUCCEEDED", "FAILED", "PENDING").
     * Should map to controlled enums in production.
     */
    @Column(nullable = false, length = 16)
    private String status;

    /** AES-GCM encrypted Primary Account Number (PAN). */
    @Lob
    @Column(nullable = false)
    private byte[] encPan;

    /** AES-GCM initialization vector (IV). */
    @Column(nullable = false, length = 16)
    private byte[] iv;

    /** AES-GCM authentication tag. */
    @Column(nullable = false, length = 16)
    private byte[] tag;

    /** Data encryption key ID (used for key rotation). */
    @Column(nullable = false, length = 64)
    private String dekKid;

    /** Masked card number (e.g., "411111******1111"). */
    @Column(nullable = false, length = 32)
    private String maskedCard;

    /** Card brand (e.g., "VISA", "MASTERCARD"). */
    @Column(nullable = false, length = 16)
    private String brand;

    /** Card expiry month (1–12). */
    @Column(nullable = false)
    private Integer expiryMonth;

    /** Card expiry year (>= current year). */
    @Column(nullable = false)
    private Integer expiryYear;

    /** Idempotency key provided by the client. */
    @Column(nullable = false, length = 36)
    private String idempotencyKey;

    /**
     * SHA-256 hash of the request payload.
     * Ensures idempotency key is not reused for different requests.
     */
    @Column(nullable = false, length = 32)
    private byte[] requestHash;

    /** Optional client-provided reference ID (e.g., invoice reference). */
    @Column(length = 128)
    private String clientReferenceId;

    /** Timestamp when the record was created. */
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** Timestamp when the record was last updated. */
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Update `updatedAt` automatically before persisting changes. */
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
