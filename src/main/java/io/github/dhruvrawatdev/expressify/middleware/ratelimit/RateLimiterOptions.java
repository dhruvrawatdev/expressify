package io.github.dhruvrawatdev.expressify.middleware.ratelimit;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Options for {@link RateLimiter} — mirrors express-rate-limit options.
 */
public class RateLimiterOptions {

    @FunctionalInterface
    public interface KeyGenerator {
        String generate(Request req);
    }

    @FunctionalInterface
    public interface Handler {
        void handle(Request req, Response res, int limit, long resetTime) throws Exception;
    }

    @FunctionalInterface
    public interface SkipFunction {
        boolean skip(Request req);
    }

    private final long windowMs;
    private final int max;
    private final String message;
    private final int statusCode;
    private final boolean headers;
    private final boolean legacyHeaders;
    private final boolean standardHeaders;
    private final KeyGenerator keyGenerator;
    private final Handler handler;
    private final SkipFunction skip;
    private final RateLimitStore store;

    private RateLimiterOptions(Builder b) {
        this.windowMs = b.windowMs;
        this.max = b.max;
        this.message = b.message;
        this.statusCode = b.statusCode;
        this.headers = b.headers;
        this.legacyHeaders = b.legacyHeaders;
        this.standardHeaders = b.standardHeaders;
        this.keyGenerator = b.keyGenerator;
        this.handler = b.handler;
        this.skip = b.skip;
        this.store = b.store;
    }

    public long getWindowMs() { return windowMs; }
    public int getMax() { return max; }
    public String getMessage() { return message; }
    public int getStatusCode() { return statusCode; }
    public boolean isHeaders() { return headers; }
    public boolean isLegacyHeaders() { return legacyHeaders; }
    public boolean isStandardHeaders() { return standardHeaders; }
    public KeyGenerator getKeyGenerator() { return keyGenerator; }
    public Handler getHandler() { return handler; }
    public SkipFunction getSkip() { return skip; }
    public RateLimitStore getStore() { return store; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long windowMs = 60_000L;  // 1 minute
        private int max = 100;
        private String message = "Too many requests, please try again later.";
        private int statusCode = 429;
        private boolean headers = true;
        private boolean legacyHeaders = true;
        private boolean standardHeaders = false;
        private KeyGenerator keyGenerator = req -> req.ip();
        private Handler handler = null;
        private SkipFunction skip = null;
        private RateLimitStore store = new MemoryRateLimitStore();

        /**
         * Length of the sliding time window in milliseconds. Default: {@code 60_000} (1 minute).
         *
         * @param ms window duration; e.g., {@code 15 * 60 * 1000} for 15 minutes
         * @return this builder
         */
        public Builder windowMs(long ms) { this.windowMs = ms; return this; }

        /**
         * Maximum number of requests allowed per key per window. Default: {@code 100}.
         *
         * @param n maximum request count; requests beyond this receive a 429 response
         * @return this builder
         */
        public Builder max(int n) { this.max = n; return this; }

        /**
         * Plain-text body sent with the 429 response when the limit is exceeded.
         * Default: {@code "Too many requests, please try again later."}.
         *
         * @param msg response body text
         * @return this builder
         */
        public Builder message(String msg) { this.message = msg; return this; }

        /**
         * HTTP status code sent when the limit is exceeded. Default: {@code 429}.
         *
         * @param code status code (RFC 6585 recommends {@code 429})
         * @return this builder
         */
        public Builder statusCode(int code) { this.statusCode = code; return this; }

        /**
         * Master switch for all rate-limit headers. Default: {@code true}.
         *
         * @param v {@code false} to suppress all rate-limit headers
         * @return this builder
         */
        public Builder headers(boolean v) { this.headers = v; return this; }

        /**
         * Emit legacy {@code X-RateLimit-Limit}, {@code X-RateLimit-Remaining},
         * and {@code X-RateLimit-Reset} headers. Default: {@code true}.
         *
         * @param v {@code false} to suppress legacy headers
         * @return this builder
         */
        public Builder legacyHeaders(boolean v) { this.legacyHeaders = v; return this; }

        /**
         * Emit IETF draft-7 combined {@code RateLimit} and {@code RateLimit-Policy} headers.
         * Default: {@code false}.
         *
         * @param v {@code true} to enable standard headers
         * @return this builder
         */
        public Builder standardHeaders(boolean v) { this.standardHeaders = v; return this; }

        /**
         * Function that derives a rate-limit key from the request.
         * Default: {@code req -> req.ip()}.
         *
         * <pre>{@code
         * // Key by authenticated user ID instead of IP
         * .keyGenerator(req -> (String) req.locals().getOrDefault("userId", req.ip()))
         * }</pre>
         *
         * @param fn function returning a non-null string key per request
         * @return this builder
         */
        public Builder keyGenerator(KeyGenerator fn) { this.keyGenerator = fn; return this; }

        /**
         * Custom handler invoked instead of the default 429 response when the limit is exceeded.
         *
         * <pre>{@code
         * .handler((req, res, limit, resetTime) -> {
         *     long retryAfter = (resetTime - System.currentTimeMillis()) / 1000;
         *     res.status(429).json(Map.of("error", "Rate limit exceeded",
         *         "retryAfter", retryAfter));
         * })
         * }</pre>
         *
         * @param fn custom 429 handler; receives request, response, limit, and reset epoch ms
         * @return this builder
         */
        public Builder handler(Handler fn) { this.handler = fn; return this; }

        /**
         * Predicate that, when it returns {@code true}, bypasses rate limiting entirely.
         *
         * <pre>{@code
         * .skip(req -> req.ip().startsWith("127."))  // skip localhost
         * }</pre>
         *
         * @param fn predicate; return {@code true} to skip rate limiting for the request
         * @return this builder
         */
        public Builder skip(SkipFunction fn) { this.skip = fn; return this; }

        /**
         * Pluggable counter store. Default: Caffeine in-memory store (not shared across JVMs).
         * Supply a Redis-backed or other distributed store for multi-instance deployments.
         *
         * @param s custom {@link RateLimitStore} implementation
         * @return this builder
         */
        public Builder store(RateLimitStore s) { this.store = s; return this; }

        public RateLimiterOptions build() { return new RateLimiterOptions(this); }
    }
}
