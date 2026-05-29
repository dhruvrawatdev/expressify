package io.github.dhruvrawatdev.expressify.middleware.slow_down;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Speed-limiting middleware — port of the {@code express-slow-down} npm package.
 *
 * <p>Requests up to {@code delayAfter} per window pass through immediately. Subsequent requests
 * within the same window are delayed by {@code (count - delayAfter) × delayMs} milliseconds,
 * capped at {@code maxDelayMs}.
 *
 * <pre>{@code
 * // Default: allow 5 req/min free, then +1 s delay per extra request
 * app.use(SlowDown.defaults());
 *
 * // Custom options
 * app.use(SlowDown.configure(SlowDownOptions.builder()
 *     .windowMs(15 * 60 * 1000)   // 15-minute window
 *     .delayAfter(100)             // first 100 free
 *     .delayMs(500)                // +500 ms per extra request
 *     .maxDelayMs(20_000)          // cap at 20 s
 *     .build()));
 * }</pre>
 */
public final class SlowDown implements RouteHandler {

    private final SlowDownOptions opts;

    /** [0] = hit count, [1] = window-start epoch ms */
    private final ConcurrentHashMap<String, long[]> store = new ConcurrentHashMap<>();

    private SlowDown(SlowDownOptions opts) { this.opts = opts; }

    /**
     * Slow-down with default options: 5 free requests per 60-second window,
     * then an additional 1 second of delay per request above the threshold,
     * keyed by client IP.
     *
     * @return a configured {@link SlowDown} handler ready to be passed to {@code app.use()}
     */
    public static SlowDown defaults() {
        return new SlowDown(SlowDownOptions.builder().build());
    }

    /**
     * Slow-down with custom options.
     *
     * <p>After this middleware runs, downstream handlers can inspect the throttle state via:
     * <pre>{@code
     * SlowDown.SlowDownInfo info = (SlowDown.SlowDownInfo) req.locals().get("slowDown");
     * long delayApplied = info.delay();    // ms this request was delayed
     * long remaining    = info.remaining(); // free requests left in window
     * }</pre>
     *
     * @param opts slow-down configuration; build with {@link SlowDownOptions#builder()}
     * @return a configured {@link SlowDown} handler ready to be passed to {@code app.use()}
     */
    public static SlowDown configure(SlowDownOptions opts) {
        return new SlowDown(opts);
    }

    @Override
    public void handle(Request req, Response res, NextFunction next) throws Exception {
        String key = resolveKey(req);
        long now = System.currentTimeMillis();
        long winMs = opts.windowMs();

        long[] entry = store.compute(key, (k, v) -> {
            if (v == null || now - v[1] >= winMs) return new long[]{1L, now};
            return new long[]{v[0] + 1, v[1]};
        });

        long count = entry[0];
        long after = opts.delayAfter();
        long delay = 0;

        if (count > after) {
            long excess = count - after;
            delay = Math.min(excess * opts.delayMs(), opts.maxDelayMs());
        }

        // Attach info for downstream handlers/logging
        req.locals().put("slowDown", new SlowDownInfo(after, count, Math.max(0, after - count), delay));

        if (delay > 0) {
            Thread.sleep(delay);
        }

        next.run();
    }

    private String resolveKey(Request req) {
        if (opts.keyHeader() != null) {
            String val = req.get(opts.keyHeader());
            if (val != null && !val.isBlank()) return val;
        }
        return req.ip();
    }

    /** Slow-down metadata attached to {@code req.locals().get("slowDown")}. */
    public record SlowDownInfo(long limit, long used, long remaining, long delay) {}
}
