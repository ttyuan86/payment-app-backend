package com.tonyyuan.paymentappbackend.service;
/**
 * Service interface for handling idempotency keys.
 *
 * <p>This interface provides methods to implement an idempotency guard,
 * ensuring that the same request (identified by a key) is not processed
 * more than once. It can be implemented using various storage mechanisms:
 * <ul>
 *     <li>In-memory store (e.g., ConcurrentHashMap) – fast, JVM-local only</li>
 *     <li>Database table – durable, shared across multiple instances</li>
 *     <li>Distributed cache (e.g., Redis) – scalable, shared across services</li>
 * </ul>
 *
 * Typical lifecycle:
 * <ol>
 *     <li>{@link #tryAcquire(String)} is called before processing to acquire a lock.</li>
 *     <li>If successful, call {@link #markPending(String)} to mark the request as processing.</li>
 *     <li>Once finished, call {@link #markCompleted(String)} to mark as successfully processed.</li>
 *     <li>{@link #release(String)} should always be called in a finally block to release locks.</li>
 * </ol>
 */
public interface IdempotencyService {

    /**
     * Attempts to acquire a lock for the given idempotency key.
     *
     * @param key unique identifier for the request
     * @return true if the lock was successfully acquired, false otherwise
     */
    boolean tryAcquire(String key);

    /**
     * Releases the lock for the given idempotency key.
     *
     * @param key unique identifier for the request
     */
    void release(String key);

    /**
     * Checks whether the given idempotency key is currently marked as pending.
     *
     * @param key unique identifier for the request
     * @return true if marked as pending, false otherwise
     */
    boolean isPending(String key);

    /**
     * Marks the given idempotency key as pending (i.e., processing started).
     *
     * @param key unique identifier for the request
     */
    void markPending(String key);

    /**
     * Marks the given idempotency key as completed (i.e., successfully processed).
     *
     * @param key unique identifier for the request
     */
    void markCompleted(String key);

    /**
     * Checks whether the given idempotency key is marked as completed.
     *
     * @param key unique identifier for the request
     * @return true if marked as completed, false otherwise
     */
    boolean isCompleted(String key);
}
