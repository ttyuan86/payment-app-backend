package com.tonyyuan.paymentappbackend.repository;


import com.tonyyuan.paymentappbackend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<Payment> findByPaymentId(String paymentId);
}