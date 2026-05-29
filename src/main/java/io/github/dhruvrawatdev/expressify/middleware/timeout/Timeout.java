package io.github.dhruvrawatdev.expressify.middleware.timeout;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request timeout middleware — port of the Node.js {@code connect-timeout} package.
 *
 * <p>Starts a countdown timer when the request arrives. If the response is not started
 * within the configured window:
 * <ol>
 *   <li>Sets {@code req.locals().get("timedout")} to {@code Boolean.TRUE}</li>
 *   <li>When {@code respond} is {@code true} (default), sends {@code 503 Service Unavailable}</li>
 * </ol>
 *
 * <p>The timer is automatically cancelled when the response begins (via a pre-send hook),
 * and can also be cancelled manually by calling the {@code Runnable} stored at
 * {@code req.locals().get("clearTimeout")}.
 *
 * <h2>Important — Java threading note</h2>
 * <p>Unlike Node.js, synchronous Java handlers run to completion on a single worker thread.
 * This means a timeout cannot interrupt a handler that is CPU-bound or blocking synchronously.
 * However, for async handlers, long I/O waits, or handlers that explicitly check the
 * {@code timedout} flag, the timeout works correctly.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Global 5-second timeout with automatic 503 response
 * app.use(Timeout.create(5000));
 *
 * // Custom timeout with manual response handling
 * app.use(Timeout.create(3000, TimeoutOptions.builder().respond(false).build()));
 * app.use((req, res, next) -> {
 *     if (Boolean.TRUE.equals(req.locals().get("timedout"))) {
 *         res.status(503).json(Map.of("error", "Request timed out"));
 *         return;
 *     }
 *     next.run();
 * });
 *
 * // Cancel the timeout early in a specific route (e.g. long-running export)
 * app.get("/export", (req, res) -> {
 *     Runnable clear = (Runnable) req.locals().get("clearTimeout");
 *     if (clear != null) clear.run();
 *     // ... process large export ...
 *     res.send("done");
 * });
 *
 * // String duration parsing
 * app.use(Timeout.create("30s"));  // 30 seconds
 * app.use(Timeout.create("2m"));   // 2 minutes
 * }</pre>
 *
 * <h2>req.locals() keys set by this middleware</h2>
 * <ul>
 *   <li>{@code "timedout"} ({@link Boolean}) — {@code false} initially; {@code true} after timeout fires</li>
 *   <li>{@code "clearTimeout"} ({@link Runnable}) — call to cancel the pending timer</li>
 * </ul>
 */
public final class Timeout {

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "expressify-timeout");
            t.setDaemon(true);
            return t;
        });

    private Timeout() {}

    /**
     * Create a timeout middleware with the given duration and default options
     * (auto-respond with 503).
     *
     * <pre>{@code app.use(Timeout.create(5000)); // 5-second timeout }</pre>
     *
     * @param milliseconds timeout window in milliseconds; must be {@code > 0}
     * @return a {@link RouteHandler} that enforces the request timeout
     * @throws IllegalArgumentException if {@code milliseconds} is not positive
     */
    public static RouteHandler create(long milliseconds) {
        return create(milliseconds, TimeoutOptions.defaults());
    }

    /**
     * Create a timeout middleware with the given duration and custom options.
     *
     * <pre>{@code
     * app.use(Timeout.create(10_000, TimeoutOptions.builder()
     *     .respond(false)  // only set the flag, don't auto-send 503
     *     .build()));
     * }</pre>
     *
     * @param milliseconds timeout window in milliseconds; must be {@code > 0}
     * @param opts         timeout options; build with {@link TimeoutOptions#builder()}
     * @return a {@link RouteHandler} that enforces the request timeout
     * @throws IllegalArgumentException if {@code milliseconds} is not positive
     */
    public static RouteHandler create(long milliseconds, TimeoutOptions opts) {
        if (milliseconds <= 0) throw new IllegalArgumentException("timeout must be > 0 ms");
        TimeoutOptions o = opts != null ? opts : TimeoutOptions.defaults();

        return (req, res, next) -> {
            AtomicBoolean done = new AtomicBoolean(false);
            ScheduledFuture<?>[] futureHolder = {null};

            // Initialise the flag so handlers can safely read it immediately
            req.locals().put("timedout", Boolean.FALSE);

            // Allow handlers to cancel the timer early
            req.locals().put("clearTimeout", (Runnable) () -> {
                done.set(true);
                ScheduledFuture<?> f = futureHolder[0];
                if (f != null) f.cancel(false);
            });

            // Cancel timer the moment the response starts being written
            res.onPreSend(() -> {
                done.set(true);
                ScheduledFuture<?> f = futureHolder[0];
                if (f != null) f.cancel(false);
            });

            futureHolder[0] = SCHEDULER.schedule(() -> {
                if (done.compareAndSet(false, true)) {
                    req.locals().put("timedout", Boolean.TRUE);
                    if (o.isRespond()) {
                        try {
                            io.undertow.server.HttpServerExchange ex = req.getExchange();
                            // setStatusCode is safe before response is started
                            if (!ex.isResponseStarted()) {
                                ex.setStatusCode(503);
                            }
                            // endExchange() is thread-safe in Undertow — signals exchange complete
                            ex.endExchange();
                        } catch (Exception ignored) {
                            // exchange already ended; nothing to do
                        }
                    }
                }
            }, milliseconds, TimeUnit.MILLISECONDS);

            next.run();
        };
    }

    /**
     * Create a timeout middleware from a human-readable duration string.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>{@code "5000"} or {@code "5000ms"} — 5 000 milliseconds</li>
     *   <li>{@code "5s"} or {@code "5 seconds"} — 5 000 milliseconds</li>
     *   <li>{@code "2m"} or {@code "2 minutes"} — 120 000 milliseconds</li>
     *   <li>{@code "1h"} or {@code "1 hour"} — 3 600 000 milliseconds</li>
     * </ul>
     *
     * <pre>{@code
     * app.use(Timeout.create("30s"));
     * app.use(Timeout.create("2m"));
     * }</pre>
     *
     * @param duration duration string
     * @return a {@link RouteHandler} that enforces the request timeout
     * @throws IllegalArgumentException if {@code duration} cannot be parsed or is not positive
     */
    public static RouteHandler create(String duration) {
        return create(parseDuration(duration));
    }

    /**
     * Create a timeout middleware from a duration string with custom options.
     *
     * @param duration duration string (see {@link #create(String)} for format)
     * @param opts     timeout options; build with {@link TimeoutOptions#builder()}
     * @return a {@link RouteHandler} that enforces the request timeout
     */
    public static RouteHandler create(String duration, TimeoutOptions opts) {
        return create(parseDuration(duration), opts);
    }

    // Internal

    static long parseDuration(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("duration must not be blank");
        String t = s.trim().toLowerCase();
        try {
            if (t.endsWith("ms")) return Long.parseLong(t.substring(0, t.length() - 2).trim());
            if (t.endsWith("seconds") || t.endsWith("second")) {
                return Long.parseLong(t.replaceAll("[^0-9]", "")) * 1_000L;
            }
            if (t.endsWith("minutes") || t.endsWith("minute")) {
                return Long.parseLong(t.replaceAll("[^0-9]", "")) * 60_000L;
            }
            if (t.endsWith("hours") || t.endsWith("hour")) {
                return Long.parseLong(t.replaceAll("[^0-9]", "")) * 3_600_000L;
            }
            if (t.endsWith("s")) return Long.parseLong(t.substring(0, t.length() - 1).trim()) * 1_000L;
            if (t.endsWith("m")) return Long.parseLong(t.substring(0, t.length() - 1).trim()) * 60_000L;
            if (t.endsWith("h")) return Long.parseLong(t.substring(0, t.length() - 1).trim()) * 3_600_000L;
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse duration: '" + s + "'", e);
        }
    }
}
