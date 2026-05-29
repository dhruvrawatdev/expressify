package io.github.dhruvrawatdev.expressify.middleware.timeout;

/**
 * Configuration options for {@link Timeout} middleware.
 *
 * <pre>{@code
 * app.use(Timeout.create(5000, TimeoutOptions.builder()
 *     .respond(false)  // set timedout flag only, don't auto-send 503
 *     .build()));
 * }</pre>
 */
public final class TimeoutOptions {

    private final boolean respond;

    private TimeoutOptions(Builder b) {
        this.respond = b.respond;
    }

    /**
     * Whether to automatically send a {@code 503 Service Unavailable} response when the
     * request times out. Default: {@code true}.
     *
     * <p>When {@code false}, only the {@code timedout} flag in {@code req.locals()} is set.
     * Use a follow-up middleware to check the flag and respond manually:
     * <pre>{@code
     * // Custom timeout response
     * app.use(Timeout.create(5000, TimeoutOptions.builder().respond(false).build()));
     * app.use((req, res, next) -> {
     *     if (Boolean.TRUE.equals(req.locals().get("timedout"))) {
     *         res.status(503).json(Map.of("error", "Request timed out"));
     *         return;
     *     }
     *     next.run();
     * });
     * }</pre>
     */
    public boolean isRespond() { return respond; }

    /** Create a builder for {@link TimeoutOptions}. */
    public static Builder builder() { return new Builder(); }

    /** Return the default options (auto-respond with 503). */
    public static TimeoutOptions defaults() { return builder().build(); }

    /** Builder for {@link TimeoutOptions}. */
    public static final class Builder {

        private boolean respond = true;

        /**
         * Set whether to auto-respond with {@code 503} on timeout.
         *
         * @param respond {@code true} to send 503 automatically (default), {@code false} to only set the flag
         * @return this builder
         */
        public Builder respond(boolean respond) { this.respond = respond; return this; }

        /** Build the {@link TimeoutOptions}. */
        public TimeoutOptions build() { return new TimeoutOptions(this); }
    }
}
