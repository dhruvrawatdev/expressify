package io.github.dhruvrawatdev.expressify.middleware.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/** Default in-memory rate-limit store backed by a ConcurrentHashMap. */
public class MemoryRateLimitStore implements RateLimitStore {

    private static final class Entry {
        volatile int count;
        final long windowStart;
        final long windowMs;

        Entry(long windowMs) {
            this.count = 1;
            this.windowStart = System.currentTimeMillis();
            this.windowMs = windowMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public int increment(String key, long windowMs) {
        Entry entry = map.compute(key, (k, existing) -> {
            if (existing == null || System.currentTimeMillis() - existing.windowStart >= existing.windowMs) {
                return new Entry(windowMs);
            }
            existing.count++;
            return existing;
        });
        return entry.count;
    }

    @Override
    public void reset(String key) {
        map.remove(key);
    }

    @Override
    public long resetTime(String key) {
        Entry e = map.get(key);
        return e == null ? System.currentTimeMillis() : e.windowStart + e.windowMs;
    }
}
