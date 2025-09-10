package com.tonyyuan.paymentappbackend.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.*;
import java.util.List;

/**
 * Request payload for creating a payment.
 * Contains cardholder details, payment amount, and associated invoice IDs.
 */
@Getter
@Setter
public class PaymentCreateRequest {

    /** First name of the cardholder (required, non-empty). */
    @NotBlank
    private String firstName;

    /** Last name of the cardholder (required, non-empty). */
    @NotBlank
    private String lastName;

    /**
     * Card number (PAN).
     * Must be 12–19 digits, digits only.
     * Stored in encrypted form (never logged or stored in plain text).
     */
    @Pattern(regexp="^[0-9]{12,19}$")
    @NotBlank
    private String cardNumber;

    /**
     * Expiry month of the card (1–12).
     */
    @Min(1)
    @Max(12)
    private int expiryMonth;

    /**
     * Expiry year of the card (must be >= 2024).
     */
    @Min(2024)
    private int expiryYear;

    /**
     * CVV/CVC security code.
     * Must be 3–4 digits, not stored in the database for PCI compliance.
     */
    @Pattern(regexp="^[0-9]{3,4}$")
    @NotBlank
    private String cvv; // Not persisted

    /**
     * Payment amount in minor units (e.g., cents).
     * Must be positive.
     */
    @Min(1)
    private long amountMinor;

    /**
     * Currency code in ISO 4217 format (e.g., USD, AUD, CNY).
     * Exactly 3 uppercase letters.
     */
    @Pattern(regexp="^[A-Z]{3}$")
    @NotBlank
    private String currency;

    /**
     * One or more invoice IDs to be paid.
     * Each ID must be 1–64 characters.
     */
    @NotEmpty
    private List<@Size(min=1, max=64) String> invoiceIds;

    /**
     * Optional client-provided reference ID.
     * Max length 128 characters.
     */
    @Size(max=128)
    private String clientReferenceId;
}
