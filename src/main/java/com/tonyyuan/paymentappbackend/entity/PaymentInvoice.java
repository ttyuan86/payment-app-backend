package com.tonyyuan.paymentappbackend.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

/**
 * Entity that links a Payment with one or more invoices.
 *
 * Features:
 *  - Each row represents the relationship between a single invoice and a payment.
 *  - Enforces uniqueness by (tenantId, invoiceId) so the same invoice
 *    cannot be paid twice for the same tenant.
 */
@Entity
@Table(
        name = "payment_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tenant_invoice", // explicit constraint name
                columnNames = {"tenant_id", "invoice_id"}
        )
)
@Getter
@Setter
public class PaymentInvoice {

    /** Surrogate primary key (auto-increment). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant identifier (multi-tenant support). */
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    /** External invoice identifier. */
    @Column(name = "invoice_id", nullable = false)
    private String invoiceId;

    /**
     * Reference to the parent Payment.
     * Many invoices can be linked to the same Payment.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_fk", nullable = false)
    private Payment payment;


}
