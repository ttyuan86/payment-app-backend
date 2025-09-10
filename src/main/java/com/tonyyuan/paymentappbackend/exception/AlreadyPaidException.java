package com.tonyyuan.paymentappbackend.exception;

import java.util.List;

/**
 * Custom business exception thrown when one or more invoices
 * have already been paid for the same tenant.
 *
 * Purpose:
 *  - Used to stop duplicate payments at the service layer.
 *  - Carries the list of invoice IDs that were already paid,
 *    so the controller or exception handler can include them
 *    in the error response.
 */
public class AlreadyPaidException extends RuntimeException {

    /** IDs of invoices that were already marked as paid. */
    private final List<String> paidInvoiceIds;

    /**
     * Constructs a new exception with a message and the list of paid invoice IDs.
     *
     * @param message        Explanation of the error.
     * @param paidInvoiceIds Invoices that caused the conflict.
     */
    public AlreadyPaidException(String message, List<String> paidInvoiceIds) {
        super(message);
        this.paidInvoiceIds = paidInvoiceIds;
    }

    /** @return the list of already paid invoice IDs */
    public List<String> getPaidInvoiceIds() {
        return paidInvoiceIds;
    }
}
