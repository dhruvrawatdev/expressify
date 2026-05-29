package io.github.dhruvrawatdev.expressify.middleware.helmet;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.util.List;
import java.util.Map;

/**
 * Helmet middleware — sets security-related HTTP response headers.
 * Mirrors the {@code helmet} npm package for Express.js.
 *
 * <pre>{@code
 * app.use(Helmet.defaults());
 *
 * app.use(Helmet.configure(HelmetOptions.builder()
 *     .frameguard("DENY")
 *     .hsts(true)
 *     .hstsMaxAge(31536000)
 *     .build()));
 * }</pre>
 */
public final class Helmet {

    private static final String DEFAULT_CSP =
            "default-src 'self'; " +
            "base-uri 'self'; " +
            "font-src 'self' https: data:; " +
            "form-action 'self'; " +
            "frame-ancestors 'self'; " +
            "img-src 'self' data:; " +
            "object-src 'none'; " +
            "script-src 'self'; " +
            "script-src-attr 'none'; " +
            "style-src 'self' https: 'unsafe-inline'; " +
            "upgrade-insecure-requests";

    private Helmet() {}

    /**
     * Apply all security headers with sensible production defaults.
     *
     * <p>Equivalent to calling {@code Helmet.configure(HelmetOptions.builder().build())}.
     * Enables: CSP, COEP, COOP, CORP, X-DNS-Prefetch-Control (off), X-Frame-Options (SAMEORIGIN),
     * hides X-Powered-By, HSTS (180 days), X-Download-Options, X-Content-Type-Options (nosniff),
     * Origin-Agent-Cluster, X-Permitted-Cross-Domain-Policies (none), Referrer-Policy (no-referrer),
     * and sets X-XSS-Protection to {@code 0}.
     *
     * @return a {@link RouteHandler} that writes all default security headers before each response
     */
    public static RouteHandler defaults() {
        return configure(HelmetOptions.builder().build());
    }

    /**
     * Apply security headers using the given options.
     *
     * <pre>{@code
     * app.use(Helmet.configure(HelmetOptions.builder()
     *     .contentSecurityPolicy(true)
     *     .hsts(true)
     *     .hstsMaxAge(31_536_000)   // 1 year
     *     .frameguard("DENY")
     *     .build()));
     * }</pre>
     *
     * @param opts the security-header configuration; build with {@link HelmetOptions#builder()}
     * @return a {@link RouteHandler} that applies the configured security headers before each response
     */
    public static RouteHandler configure(HelmetOptions opts) {
        return (req, res, next) -> {
            applyHeaders(res, opts);
            next.run();
        };
    }

    private static void applyHeaders(Response res, HelmetOptions opts) {
        // Content-Security-Policy
        if (opts.isContentSecurityPolicy()) {
            Map<String, List<String>> dirs = opts.getCspDirectives();
            if (dirs != null && !dirs.isEmpty()) {
                StringBuilder csp = new StringBuilder();
                dirs.forEach((key, values) -> {
                    if (csp.length() > 0) csp.append("; ");
                    csp.append(key);
                    if (values != null && !values.isEmpty()) csp.append(' ').append(String.join(" ", values));
                });
                res.header("Content-Security-Policy", csp.toString());
            } else {
                res.header("Content-Security-Policy", DEFAULT_CSP);
            }
        }

        // Cross-Origin-Embedder-Policy
        if (opts.isCrossOriginEmbedderPolicy()) {
            res.header("Cross-Origin-Embedder-Policy", "require-corp");
        }

        // Cross-Origin-Opener-Policy
        if (opts.getCrossOriginOpenerPolicy() != null) {
            res.header("Cross-Origin-Opener-Policy", opts.getCrossOriginOpenerPolicy());
        }

        // Cross-Origin-Resource-Policy
        if (opts.getCrossOriginResourcePolicy() != null) {
            res.header("Cross-Origin-Resource-Policy", opts.getCrossOriginResourcePolicy());
        }

        // X-DNS-Prefetch-Control
        if (opts.isDnsPrefetchControl()) {
            res.header("X-DNS-Prefetch-Control", opts.isDnsPrefetchControlAllow() ? "on" : "off");
        }

        // X-Frame-Options
        if (opts.getFrameguard() != null) {
            res.header("X-Frame-Options", opts.getFrameguard());
        }

        // Hide X-Powered-By
        if (opts.isHidePoweredBy()) {
            res.removeHeader("X-Powered-By");
        }

        // Strict-Transport-Security
        if (opts.isHsts()) {
            StringBuilder hsts = new StringBuilder("max-age=").append(opts.getHstsMaxAge());
            if (opts.isHstsIncludeSubDomains()) hsts.append("; includeSubDomains");
            if (opts.isHstsPreload())           hsts.append("; preload");
            res.header("Strict-Transport-Security", hsts.toString());
        }

        // X-Download-Options (IE)
        if (opts.isIeNoOpen()) {
            res.header("X-Download-Options", "noopen");
        }

        // X-Content-Type-Options
        if (opts.isNoSniff()) {
            res.header("X-Content-Type-Options", "nosniff");
        }

        // Origin-Agent-Cluster
        if (opts.isOriginAgentCluster()) {
            res.header("Origin-Agent-Cluster", "?1");
        }

        // X-Permitted-Cross-Domain-Policies
        if (opts.getPermittedCrossDomainPolicies() != null) {
            res.header("X-Permitted-Cross-Domain-Policies", opts.getPermittedCrossDomainPolicies());
        }

        // Referrer-Policy
        if (opts.getReferrerPolicy() != null) {
            res.header("Referrer-Policy", opts.getReferrerPolicy());
        }

        // X-XSS-Protection (disabled by default — helmet v5+ sets 0)
        res.header("X-XSS-Protection", opts.isXssProtection() ? "1; mode=block" : "0");
    }
}
