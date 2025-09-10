package com.tonyyuan.paymentappbackend.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory idempotency service for demo/interview purposes.
 * - Uses ConcurrentHashMap to track states (PENDING / COMPLETED).
 * - Not production-ready (no TTL, no multi-instance safety).
 */
@Service
public class InMemoryIdempotencyService implements IdempotencyService {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key) {
        // Only succeed if no one has acquired yet
        return store.putIfAbsent(key, "PENDING") == null;
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }

    @Override
    public boolean isPending(String key) {
        return "PENDING".equals(store.get(key));
    }

    @Override
    public void markPending(String key) {
        store.put(key, "PENDING");
    }

    @Override
    public void markCompleted(String key) {
        store.put(key, "COMPLETED");
    }

    @Override
    public boolean isCompleted(String key) {
        return "COMPLETED".equals(store.get(key));
    }
}