package io.github.dhruvrawatdev.expressify.middleware.slow_down;

/**
 * Configuration options for {@link SlowDown} — mirrors express-slow-down.
 */
public class SlowDownOptions {

    private final long windowMs;
    private final int delayAfter;
    private final long delayMs;
    private final long maxDelayMs;
    private final String keyHeader;

    private SlowDownOptions(Builder b) {
        this.windowMs = b.windowMs;
        this.delayAfter = b.delayAfter;
        this.delayMs = b.delayMs;
        this.maxDelayMs = b.maxDelayMs;
        this.keyHeader = b.keyHeader;
    }

    /** Length of the rolling time window in milliseconds. Default: 60 000. */
    public long windowMs() { return windowMs; }
    /** Number of requests allowed before slowing begins. Default: 5. */
    public int delayAfter() { return delayAfter; }
    /** Base delay per excess request in milliseconds. Default: 1 000. */
    public long delayMs() { return delayMs; }
    /** Absolute maximum delay cap in milliseconds. Default: unlimited. */
    public long maxDelayMs() { return maxDelayMs; }
    /** Request header used as the client key when IP is unavailable. Default: null (use IP). */
    public String keyHeader() { return keyHeader; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long windowMs = 60_000L;
        private int delayAfter = 5;
        private long delayMs = 1_000L;
        private long maxDelayMs = Long.MAX_VALUE;
        private String keyHeader  = null;

        /**
         * Length of the rolling time window in milliseconds. Default: {@code 60_000} (1 minute).
         *
         * @param v window duration in ms; e.g., {@code 15 * 60 * 1000} for 15 minutes
         * @return this builder
         */
        public Builder windowMs(long v) { this.windowMs = v; return this; }

        /**
         * Number of requests allowed through without any delay in each window.
         * Default: {@code 5}. Requests above this count are slowed.
         *
         * @param v free-request count per window per client
         * @return this builder
         */
        public Builder delayAfter(int v) { this.delayAfter = v; return this; }

        /**
         * Base delay per excess request in milliseconds.
         * Actual delay = {@code (count - delayAfter) × delayMs}, capped at {@code maxDelayMs}.
         * Default: {@code 1_000} (1 second).
         *
         * @param v delay increment in ms per request above the threshold
         * @return this builder
         */
        public Builder delayMs(long v) { this.delayMs = v; return this; }

        /**
         * Absolute maximum delay cap in milliseconds regardless of excess count.
         * Default: {@code Long.MAX_VALUE} (unlimited).
         *
         * @param v maximum delay in ms; e.g., {@code 20_000} to cap at 20 seconds
         * @return this builder
         */
        public Builder maxDelayMs(long v) { this.maxDelayMs = v; return this; }

        /**
         * Request header used as the client key when the IP address is unavailable or unreliable
         * (e.g., behind a load balancer with {@code X-Forwarded-For}).
         * Default: {@code null} (falls back to {@code req.ip()}).
         *
         * @param v header name to use as the throttle key, or {@code null} to use IP
         * @return this builder
         */
        public Builder keyHeader(String v){ this.keyHeader = v; return this; }

        public SlowDownOptions build() { return new SlowDownOptions(this); }
    }
}
