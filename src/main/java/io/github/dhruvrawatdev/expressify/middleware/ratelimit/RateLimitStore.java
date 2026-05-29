package io.github.dhruvrawatdev.expressify.middleware.ratelimit;

/** Pluggable backing store for rate-limit counters. */
public interface RateLimitStore {

    /**
     * Increment the hit count for {@code key} and return its current count.
     * The store must reset the counter after {@code windowMs} milliseconds from the first hit.
     */
    int increment(String key, long windowMs);

    /** Reset the counter for {@code key} immediately. */
    void reset(String key);

    /** Return the epoch-millis at which the current window for {@code key} expires. */
    long resetTime(String key);
}
