package com.tonyyuan.paymentappbackend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Simple rate limiter service using Bucket4j.
 *
 * Current setup:
 * - Allows up to 100 requests per minute (global limit).
 * - This is applied in the controller level for demonstration purposes.
 *
 * Notes:
 * - Because we do not maintain user accounts or sessions in this demo,
 *   the rate limit is global for the whole application.
 * - In a production system, rate limiting should typically be
 *   applied per user, per API key, or per client IP.
 * - This often requires having a user/tenant table and session or token
 *   management to track and enforce rate limits at the correct granularity.
 */
@Service
public class RateLimiterService {

    private final Bucket bucket;

    public RateLimiterService() {
        // Define a limit of 100 requests per minute
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        this.bucket = Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Try to consume one token from the bucket.
     * @return true if a request is allowed, false if the limit has been reached.
     */
    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }
}


