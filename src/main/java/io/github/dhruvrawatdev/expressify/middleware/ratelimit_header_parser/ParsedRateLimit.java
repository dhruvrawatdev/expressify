package io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser;

import java.time.Instant;

/**
 * Parsed representation of HTTP rate-limit headers.
 *
 * <p>Mirrors the return type of the {@code ratelimit-header-parser} npm package.
 * All fields may be -1 / null when the corresponding header was absent.
 */
public final class ParsedRateLimit {

    private final int limit;
    private final int used;
    private final int remaining;
    private final Instant reset;

    public ParsedRateLimit(int limit, int used, int remaining, Instant reset) {
        this.limit = limit;
        this.used = used;
        this.remaining = remaining;
        this.reset = reset;
    }

    /** Maximum number of requests permitted in the current window (-1 if unknown). */
    public int getLimit() { return limit; }
    /** Number of requests already used in the current window (-1 if unknown). */
    public int getUsed() { return used; }
    /** Number of requests remaining in the current window. */
    public int getRemaining() { return remaining; }
    /** Time at which the current window resets (null if unknown). */
    public Instant getReset() { return reset; }

    @Override
    public String toString() {
        return "ParsedRateLimit{limit=" + limit + ", used=" + used
                + ", remaining=" + remaining + ", reset=" + reset + "}";
    }
}
