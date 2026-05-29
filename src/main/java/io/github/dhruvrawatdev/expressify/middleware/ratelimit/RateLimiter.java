package io.github.dhruvrawatdev.expressify.middleware.ratelimit;

import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Rate-limiting middleware — mirrors express-rate-limit.
 *
 * <pre>{@code
 * app.use(RateLimiter.defaults());
 *
 * app.use(RateLimiter.configure(RateLimiterOptions.builder()
 *     .windowMs(15 * 60 * 1000)
 *     .max(100)
 *     .message("Too many requests")
 *     .build()));
 * }</pre>
 */
public final class RateLimiter implements RouteHandler {

    private final RateLimiterOptions opts;

    private RateLimiter(RateLimiterOptions opts) {
        this.opts = opts;
    }

    /**
     * Rate limiter with default options: 100 requests per 60-second window, keyed by client IP.
     *
     * <p>When the limit is exceeded the middleware responds with {@code 429 Too Many Requests}
     * and the message {@code "Too many requests, please try again later."}.
     * Legacy {@code X-RateLimit-*} headers are sent on every response.
     *
     * @return a configured {@link RateLimiter} ready to be passed to {@code app.use()}
     */
    public static RateLimiter defaults() {
        return new RateLimiter(RateLimiterOptions.builder().build());
    }

    /**
     * Rate limiter with custom options.
     *
     * <pre>{@code
     * app.use(RateLimiter.configure(RateLimiterOptions.builder()
     *     .windowMs(15 * 60 * 1000)   // 15-minute window
     *     .max(100)                    // 100 requests per window per IP
     *     .standardHeaders(true)       // emit IETF RateLimit-* headers
     *     .legacyHeaders(false)        // suppress X-RateLimit-* headers
     *     .message("Slow down!")
     *     .build()));
     * }</pre>
     *
     * <p>After the middleware runs, downstream handlers can read the info object via:
     * <pre>{@code
     * RateLimitInfo info = (RateLimitInfo) req.locals().get("rateLimit");
     * int remaining = info.remaining();
     * }</pre>
     *
     * @param opts rate-limit configuration; build with {@link RateLimiterOptions#builder()}
     * @return a configured {@link RateLimiter} ready to be passed to {@code app.use()}
     */
    public static RateLimiter configure(RateLimiterOptions opts) {
        return new RateLimiter(opts);
    }

    @Override
    public void handle(Request req, Response res, NextFunction next) throws Exception {
        if (opts.getSkip() != null && opts.getSkip().skip(req)) {
            next.run();
            return;
        }

        String key = opts.getKeyGenerator().generate(req);
        int count = opts.getStore().increment(key, opts.getWindowMs());
        long resetTime = opts.getStore().resetTime(key);
        int limit = opts.getMax();
        int remaining = Math.max(0, limit - count);

        // Attach info to request for downstream use
        req.locals().put("rateLimit", new RateLimitInfo(limit, remaining, resetTime));

        if (opts.isLegacyHeaders()) {
            // Legacy X-RateLimit-* headers (widely supported by clients)
            res.header("X-RateLimit-Limit",     String.valueOf(limit));
            res.header("X-RateLimit-Remaining", String.valueOf(remaining));
            res.header("X-RateLimit-Reset",     String.valueOf(resetTime / 1000L));
            res.header("Date", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)));
        }
        if (opts.isStandardHeaders()) {
            // IETF draft-7 combined header format
            long windowSec = opts.getWindowMs() / 1000L;
            long resetSec  = Math.max(0L, (resetTime - System.currentTimeMillis()) / 1000L);
            res.header("RateLimit-Policy", limit + ";w=" + windowSec);
            res.header("RateLimit", "limit=" + limit + ", remaining=" + remaining + ", reset=" + resetSec);
        }

        if (count > limit) {
            if (opts.getHandler() != null) {
                opts.getHandler().handle(req, res, limit, resetTime);
            } else {
                res.status(opts.getStatusCode()).send(opts.getMessage());
            }
            return;
        }

        next.run();
    }
}
