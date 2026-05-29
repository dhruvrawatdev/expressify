package io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser;

/**
 * Options for {@link RateLimitHeaderParser}.
 *
 * <p>The {@code reset} option controls how the {@code *-Reset} header value is interpreted.
 * Mirrors the {@code options.reset} field of the {@code ratelimit-header-parser} npm package.
 */
public final class RateLimitHeaderParserOptions {

    public enum ResetType {
        /** Detect automatically: letters → date string, &gt;1 billion → Unix timestamp, else seconds. */
        AUTO,
        /** Value is an HTTP-date string (RFC 1123 / ISO-8601). */
        DATE,
        /** Value is a Unix epoch in seconds. */
        UNIX,
        /** Value is seconds from now. */
        SECONDS,
        /** Value is milliseconds from now. */
        MILLISECONDS
    }

    private final ResetType resetType;

    private RateLimitHeaderParserOptions(Builder b) {
        this.resetType = b.resetType;
    }

    public ResetType getResetType() { return resetType; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ResetType resetType = ResetType.AUTO;

        /** How to interpret the reset header value (default: AUTO). */
        public Builder reset(ResetType type) { this.resetType = type; return this; }

        public RateLimitHeaderParserOptions build() { return new RateLimitHeaderParserOptions(this); }
    }
}
