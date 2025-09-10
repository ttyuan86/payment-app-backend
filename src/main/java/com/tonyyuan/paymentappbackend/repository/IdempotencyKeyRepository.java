package com.tonyyuan.paymentappbackend.repository;

import com.tonyyuan.paymentappbackend.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository interface for managing {@link IdempotencyKey} entities.
 *
 * Purpose:
 *  - Provides CRUD operations for idempotency key records.
 *  - Helps ensure requests with the same tenantId + idempotencyKey
 *    are processed only once.
 *
 * Typical usage:
 *  - Before creating a payment, check if an idempotency key
 *    already exists for the given tenant.
 *  - If found, return the stored response (or mark it as completed).
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * Finds an idempotency key by tenantId and key.
     *
     * @param tenantId The tenant/client identifier.
     * @param key      The unique idempotency key.
     * @return Optional containing the IdempotencyKey if found.
     */
    Optional<IdempotencyKey> findByTenantIdAndIdempotencyKey(String tenantId, String key);
}
