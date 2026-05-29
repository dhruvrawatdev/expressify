package io.github.dhruvrawatdev.expressify.middleware.proxy;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * HTTP reverse-proxy middleware — forwards incoming requests to a backend server
 * and streams the response back to the client.
 *
 * <p>Mirrors the behaviour of the Node.js
 * <a href="https://github.com/chimurai/http-proxy-middleware">http-proxy-middleware</a> package.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Basic proxy — forward /api/* to a backend service
 * app.use("/api", Expressify.createProxyMiddleware(
 *     ProxyOptions.builder()
 *         .target("http://backend:3000")
 *         .changeOrigin(true)
 *         .build()));
 *
 * // With path rewriting: /api/users  ->  /users
 * app.use("/api", Expressify.createProxyMiddleware(
 *     ProxyOptions.builder()
 *         .target("http://backend:3000")
 *         .changeOrigin(true)
 *         .pathRewrite("^/api", "")
 *         .build()));
 *
 * // With error handling
 * app.use("/api", Expressify.createProxyMiddleware(
 *     ProxyOptions.builder()
 *         .target("http://backend:3000")
 *         .on(ProxyOptions.OnEvents.builder()
 *             .error((err, req, res) ->
 *                 res.status(502).json(Map.of("error", "Bad Gateway")))
 *             .build())
 *         .build()));
 * }</pre>
 *
 * <h3>Implementation notes</h3>
 * <ul>
 *   <li>Uses Java 17's built-in {@link java.net.http.HttpClient} — no third-party HTTP library needed.</li>
 *   <li>Path rewrite rules are applied in insertion order; the first matching regex wins.</li>
 *   <li>{@link ProxyOptions#changeOrigin} rewrites the {@code Host} header to the target host:port.</li>
 *   <li>All original request headers are forwarded except {@code Host} (handled by changeOrigin)
 *       and hop-by-hop headers ({@code Connection}, {@code Transfer-Encoding}, etc.).</li>
 *   <li>The response body is fully buffered before forwarding (not streamed) to simplify
 *       the Java HttpClient integration.</li>
 * </ul>
 */
public final class ProxyMiddleware {

    private static final Logger log = LoggerFactory.getLogger(ProxyMiddleware.class);

    /** Hop-by-hop headers that must not be forwarded. */
    private static final java.util.Set<String> HOP_BY_HOP = java.util.Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade"
    );

    private final ProxyOptions options;
    private final HttpClient httpClient;
    private final List<CompiledRewriteRule> rewriteRules;
    private final URI targetUri;

    private ProxyMiddleware(ProxyOptions options) {
        this.options = options;
        this.targetUri = parseTarget(options.target);
        this.rewriteRules = compileRules(options.pathRewrite);

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(options.timeout));

        if (options.followRedirects) {
            clientBuilder.followRedirects(HttpClient.Redirect.NORMAL);
        } else {
            clientBuilder.followRedirects(HttpClient.Redirect.NEVER);
        }

        this.httpClient = clientBuilder.build();
    }

    /**
     * Create a proxy {@link RouteHandler} from the given options.
     *
     * @param options proxy configuration; see {@link ProxyOptions#builder()}
     * @return a {@link RouteHandler} suitable for {@code app.use()} or route-level registration
     */
    public static RouteHandler create(ProxyOptions options) {
        ProxyMiddleware proxy = new ProxyMiddleware(options);
        return proxy.toHandler();
    }

    // Core middleware logic

    private RouteHandler toHandler() {
        return (req, res, next) -> {
            // 1. Determine the upstream path (after stripping mount prefix and rewriting)
            String originalPath = req.getRoutingPath();
            String queryString  = req.getExchange().getQueryString();
            String upstreamPath = applyPathRewrite(originalPath);

            // Build the upstream URI: target-base + rewritten-path + original query
            String targetBase = targetUri.toString();
            // Strip trailing slash from targetBase to avoid double slashes
            if (targetBase.endsWith("/") && upstreamPath.startsWith("/")) {
                targetBase = targetBase.substring(0, targetBase.length() - 1);
            }
            String fullUri = targetBase + upstreamPath
                    + (queryString != null && !queryString.isEmpty() ? "?" + queryString : "");

            URI upstreamUri;
            try {
                upstreamUri = new URI(fullUri);
            } catch (java.net.URISyntaxException e) {
                next.error(new RuntimeException("ProxyMiddleware: malformed upstream URI: " + fullUri, e));
                return;
            }

            // 2. Read original request body (for POST/PUT/PATCH etc.)
            byte[] requestBody = req.getRawBody();

            // 3. Build the upstream HttpRequest
            HttpRequest.Builder proxyReqBuilder = HttpRequest.newBuilder()
                    .uri(upstreamUri)
                    .timeout(Duration.ofMillis(options.timeout));

            // Forward original method + body
            String method = req.method();
            HttpRequest.BodyPublisher bodyPublisher = (requestBody != null && requestBody.length > 0)
                    ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                    : HttpRequest.BodyPublishers.noBody();

            proxyReqBuilder.method(method, bodyPublisher);

            // 4. Forward all headers (skip hop-by-hop)
            req.getExchange().getRequestHeaders().forEach(header -> {
                String name  = header.getHeaderName().toString();
                String value = header.getFirst();
                if (name == null || value == null) return;
                if (HOP_BY_HOP.contains(name.toLowerCase())) return;
                if ("host".equalsIgnoreCase(name)) return; // handled by changeOrigin / below
                try {
                    proxyReqBuilder.header(name, value);
                } catch (IllegalArgumentException ignored) {
                    // Some headers (like :method) cannot be set on HttpRequest
                }
            });

            // 5. changeOrigin — rewrite Host to match the target
            if (options.changeOrigin) {
                int port = targetUri.getPort();
                String host = targetUri.getHost()
                        + (port != -1 && port != 80 && port != 443 ? ":" + port : "");
                try {
                    proxyReqBuilder.header("Host", host);
                } catch (IllegalArgumentException ignored) {}
            }

            // 6. Fire on.proxyReq hook (allows caller to add/override headers)
            if (options.on.proxyReq != null) {
                try {
                    options.on.proxyReq.accept(proxyReqBuilder, req);
                } catch (Exception e) {
                    log.warn("ProxyMiddleware: on.proxyReq threw an exception", e);
                }
            }

            // 7. Execute the upstream request
            HttpRequest proxyReq = proxyReqBuilder.build();
            HttpResponse<byte[]> proxyResp;
            try {
                proxyResp = httpClient.send(proxyReq, HttpResponse.BodyHandlers.ofByteArray());
            } catch (Exception e) {
                // Network error — fire on.error or propagate
                handleProxyError(e, req, res, next);
                return;
            }

            // 8. Fire on.proxyRes hook
            if (options.on.proxyRes != null) {
                try {
                    options.on.proxyRes.accept(proxyResp, req, res);
                } catch (Exception e) {
                    log.warn("ProxyMiddleware: on.proxyRes threw an exception", e);
                }
            }

            // 9. If the response was already committed by on.proxyRes, stop here
            if (res.isCommitted()) return;

            // 10. Forward status code
            res.status(proxyResp.statusCode());

            // 11. Forward response headers (skip hop-by-hop)
            proxyResp.headers().map().forEach((name, values) -> {
                if (HOP_BY_HOP.contains(name.toLowerCase())) return;
                if (values.isEmpty()) return;
                // Set the first value; Express/http-proxy-middleware behaviour
                res.header(name, values.get(0));
            });

            // 12. Send response body
            byte[] responseBody = proxyResp.body();
            if (responseBody != null && responseBody.length > 0) {
                res.send(responseBody);
            } else {
                res.end();
            }
        };
    }

    // Helpers

    private void handleProxyError(Exception e, io.github.dhruvrawatdev.expressify.http.Request req,
                                  io.github.dhruvrawatdev.expressify.http.Response res,
                                  io.github.dhruvrawatdev.expressify.router.handler.NextFunction next) {
        log.debug("ProxyMiddleware: upstream request failed: {}", e.getMessage());
        if (options.on.error != null) {
            try {
                options.on.error.accept(e, req, res);
            } catch (Exception ex) {
                log.warn("ProxyMiddleware: on.error threw an exception", ex);
            }
        } else {
            try {
                next.error(e);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private String applyPathRewrite(String path) {
        for (CompiledRewriteRule rule : rewriteRules) {
            if (rule.pattern.matcher(path).find()) {
                String rewritten = rule.pattern.matcher(path).replaceAll(rule.replacement);
                log.debug("ProxyMiddleware: pathRewrite {} -> {}", path, rewritten);
                return rewritten;
            }
        }
        return path;
    }

    private static URI parseTarget(String target) {
        String t = target.trim();
        if (!t.endsWith("/")) t = t; // keep as-is — trailing slash is not added
        try {
            return new URI(t);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("ProxyOptions.target is not a valid URI: " + target, e);
        }
    }

    private static List<CompiledRewriteRule> compileRules(Map<String, String> rules) {
        List<CompiledRewriteRule> compiled = new ArrayList<>(rules.size());
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            compiled.add(new CompiledRewriteRule(
                    Pattern.compile(entry.getKey()),
                    entry.getValue()));
        }
        return compiled;
    }

    // Private inner types

    private record CompiledRewriteRule(Pattern pattern, String replacement) {}
}
