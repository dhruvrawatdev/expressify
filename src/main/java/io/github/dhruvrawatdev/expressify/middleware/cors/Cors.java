package io.github.dhruvrawatdev.expressify.middleware.cors;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.Set;

/**
 * CORS middleware — mirrors the Express.js {@code cors} npm package.
 *
 * <pre>{@code
 * // Allow all origins (development)
 * app.use(Cors.all());
 *
 * // Fine-grained control
 * app.use(Cors.configure(CorsOptions.builder()
 *     .origin("https://example.com")
 *     .credentials(true)
 *     .maxAge(86400)
 *     .build()));
 * }</pre>
 */
public final class Cors {

    private Cors() {}

    /**
     * Allow all origins with credentials reflected.
     * Equivalent to {@code cors()} with no options in Express.
     * Alias: {@link #allow()}.
     *
     * <p>Sets {@code Access-Control-Allow-Origin} to the echoed request origin (or {@code *}
     * when no {@code Origin} header is present), adds {@code Access-Control-Allow-Credentials: true},
     * and auto-responds to {@code OPTIONS} preflight with {@code 204 No Content}.
     *
     * @return a {@link RouteHandler} that applies permissive CORS headers to every request
     */
    public static RouteHandler all() {
        return (req, res, next) -> {
            String origin = req.get("Origin");
            if (origin != null) {
                res.header("Access-Control-Allow-Origin", origin);
                res.header("Access-Control-Allow-Credentials", "true");
                res.vary("Origin");
            } else {
                res.header("Access-Control-Allow-Origin", "*");
            }
            res.header("Access-Control-Allow-Methods", "GET,HEAD,PUT,PATCH,POST,DELETE,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With");
            if ("OPTIONS".equalsIgnoreCase(req.method())) {
                res.status(204).end();
            } else {
                next.run();
            }
        };
    }

    /**
     * Alias for {@link #all()} — allow all origins.
     *
     * @return same handler as {@link #all()}
     */
    public static RouteHandler allow() { return all(); }

    /**
     * Configure CORS with explicit options.
     * Matches the behaviour of {@code require('cors')(options)} in Express.
     *
     * <pre>{@code
     * app.use(Cors.configure(CorsOptions.builder()
     *     .origins("https://app.example.com", "https://admin.example.com")
     *     .credentials(true)
     *     .maxAge(86400)
     *     .build()));
     * }</pre>
     *
     * @param opts the CORS policy; build with {@link CorsOptions#builder()}
     * @return a {@link RouteHandler} that applies the configured CORS policy
     */
    public static RouteHandler configure(CorsOptions opts) {
        return (req, res, next) -> {
            String requestOrigin = req.get("Origin");

            if (opts.getOriginCallback() != null) {
                // Dynamic origin resolution — call the callback synchronously
                boolean[] allowed   = {false};
                Exception[] cbError = {null};
                opts.getOriginCallback().check(requestOrigin, (err, ok) -> {
                    cbError[0]  = err;
                    allowed[0]  = Boolean.TRUE.equals(ok);
                });
                if (cbError[0] != null) { next.error(cbError[0]); return; }
                if (!allowed[0])         { next.run(); return; }
                String ao = requestOrigin != null ? requestOrigin : "*";
                res.header("Access-Control-Allow-Origin", ao);
                if (requestOrigin != null) res.vary("Origin");
            } else {
                String allowedOrigin = resolveOrigin(requestOrigin, opts.getOrigins());
                if (allowedOrigin == null) { next.run(); return; }
                res.header("Access-Control-Allow-Origin", allowedOrigin);
                if (!"*".equals(allowedOrigin)) res.vary("Origin");
            }

            if (opts.isCredentials()) {
                res.header("Access-Control-Allow-Credentials", "true");
            }
            if (!opts.getMethods().isEmpty()) {
                res.header("Access-Control-Allow-Methods", String.join(",", opts.getMethods()));
            }
            if (!opts.getAllowHeaders().isEmpty()) {
                res.header("Access-Control-Allow-Headers", String.join(",", opts.getAllowHeaders()));
            } else if (requestOrigin != null) {
                // Reflect requested headers when none are configured
                String requested = req.get("Access-Control-Request-Headers");
                if (requested != null) {
                    res.header("Access-Control-Allow-Headers", requested);
                    res.vary("Access-Control-Request-Headers");
                }
            }
            if (!opts.getExposedHeaders().isEmpty()) {
                res.header("Access-Control-Expose-Headers", String.join(",", opts.getExposedHeaders()));
            }
            if (opts.getMaxAge() > 0) {
                res.header("Access-Control-Max-Age", String.valueOf(opts.getMaxAge()));
            }

            if ("OPTIONS".equalsIgnoreCase(req.method())) {
                if (opts.isPreflightContinue()) {
                    next.run();
                } else {
                    res.status(opts.getOptionsSuccessStatus()).end();
                }
            } else {
                next.run();
            }
        };
    }

    private static String resolveOrigin(String requestOrigin, Set<String> allowedOrigins) {
        if (allowedOrigins.isEmpty()) return "*";
        if (allowedOrigins.contains("*")) return requestOrigin != null ? requestOrigin : "*";
        if (requestOrigin == null)    return null;
        return allowedOrigins.contains(requestOrigin) ? requestOrigin : null;
    }
}
