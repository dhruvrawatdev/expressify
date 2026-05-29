package io.github.dhruvrawatdev.expressify.middleware.ratelimit;

/** Snapshot of the rate-limit state for a single key, attached to each request. */
public class RateLimitInfo {

    private final int limit;
    private final int remaining;
    private final long resetTime; // epoch-millis

    public RateLimitInfo(int limit, int remaining, long resetTime) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetTime = resetTime;
    }

    public int getLimit() { return limit; }
    public int getRemaining() { return remaining; }
    public long getResetTime() { return resetTime; }
}
