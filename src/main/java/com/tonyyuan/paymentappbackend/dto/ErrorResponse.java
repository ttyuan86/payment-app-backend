package com.tonyyuan.paymentappbackend.dto;


import lombok.*;

import java.util.List;

/**
 * Unified error response returned by API endpoints.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@ToString
public class ErrorResponse {

    private final int status;                  // HTTP status code
    private final String error;                // Short title (e.g., "Bad Request", "Conflict")
    private final String message;              // Detailed error description
    private final List<String> paidInvoiceIds; // Optional: conflict details (e.g., already-paid invoices)
    private final String path;                 // Request path (optional)
}
