package io.github.dhruvrawatdev.expressify.middleware.response_time;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

/**
 * Response time header middleware — port of the Node.js {@code response-time} package.
 *
 * <p>Records the start time on arrival and registers a pre-send hook that writes
 * the elapsed milliseconds into {@code X-Response-Time} (or a custom header) before
 * the first byte is sent to the client.
 *
 * <pre>{@code
 * app.use(ResponseTime.create());
 * app.use(ResponseTime.create(ResponseTimeOptions.builder()
 *     .digits(0)
 *     .header("X-Request-Duration")
 *     .suffix(false)
 *     .build()));
 * }</pre>
 */
public class ResponseTime {

    private ResponseTime() {}

    /**
     * Record and emit response time with defaults: {@code X-Response-Time} header,
     * 3 decimal digits, {@code "ms"} suffix (e.g., {@code X-Response-Time: 12.345ms}).
     *
     * @return a {@link RouteHandler} that writes elapsed time to {@code X-Response-Time}
     *         before the first byte of each response
     */
    public static RouteHandler create() {
        return create(ResponseTimeOptions.defaults());
    }

    /**
     * Record and emit response time with custom options.
     *
     * <pre>{@code
     * app.use(ResponseTime.create(ResponseTimeOptions.builder()
     *     .header("X-Request-Duration")
     *     .digits(0)          // integer milliseconds only
     *     .suffix(false)      // omit the "ms" suffix
     *     .build()));
     * }</pre>
     *
     * @param options response-time configuration; build with {@link ResponseTimeOptions#builder()}
     * @return a {@link RouteHandler} that writes elapsed milliseconds to the configured header
     */
    public static RouteHandler create(ResponseTimeOptions options) {
        return (req, res, next) -> {
            long startNs = System.nanoTime();
            res.onPreSend(() -> {
                if (res.getHeader(options.getHeader()) == null) {
                    double ms  = (System.nanoTime() - startNs) / 1_000_000.0;
                    String fmt = "%." + options.getDigits() + "f";
                    String val = String.format(fmt, ms);
                    if (options.hasSuffix()) val += "ms";
                    res.set(options.getHeader(), val);
                }
            });
            next.run();
        };
    }
}
