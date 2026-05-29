package io.github.dhruvrawatdev.expressify.middleware.proxy;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Configuration for {@link ProxyMiddleware}.
 *
 * <p>Mirrors the options object accepted by the Node.js
 * {@code http-proxy-middleware} package's {@code createProxyMiddleware()}.
 *
 * <h3>Minimal usage</h3>
 * <pre>{@code
 * app.use("/api", Expressify.createProxyMiddleware(
 *     ProxyOptions.builder()
 *         .target("http://backend:3000")
 *         .changeOrigin(true)
 *         .build()));
 * }</pre>
 *
 * <h3>With path rewriting</h3>
 * <pre>{@code
 * ProxyOptions opts = ProxyOptions.builder()
 *     .target("http://backend:3000")
 *     .changeOrigin(true)
 *     .pathRewrite("^/api", "")          // /api/users  ->  /users
 *     .build();
 * }</pre>
 *
 * <h3>With event callbacks</h3>
 * <pre>{@code
 * ProxyOptions opts = ProxyOptions.builder()
 *     .target("http://backend:3000")
 *     .on(ProxyOptions.OnEvents.builder()
 *         .error((err, req, res) -> res.status(502).json(Map.of("error", "Bad Gateway")))
 *         .proxyReq((proxyReqBuilder, req) -> proxyReqBuilder.header("X-Internal", "true"))
 *         .build())
 *     .build();
 * }</pre>
 */
public final class ProxyOptions {

    /** Backend server URL to proxy requests to (e.g. {@code "http://localhost:8080"}). */
    public final String target;

    /**
     * When {@code true}, rewrite the {@code Host} header to match the target origin.
     * This is almost always required when the backend validates the Host header.
     */
    public final boolean changeOrigin;

    /**
     * Path rewrite rules — applied in order; first match wins.
     * Key = regex pattern string, Value = replacement string.
     * E.g. {@code {"^/api": ""}} rewrites {@code /api/users} to {@code /users}.
     */
    public final Map<String, String> pathRewrite;

    /** Event callback hooks. */
    public final OnEvents on;

    /**
     * When {@code true}, follow HTTP redirects from the backend.
     * Defaults to {@code false} (redirect responses are forwarded as-is to the client).
     */
    public final boolean followRedirects;

    /**
     * Connection timeout to the backend in milliseconds.
     * Defaults to {@code 30000} (30 seconds).
     */
    public final long timeout;

    private ProxyOptions(Builder b) {
        this.target = b.target;
        this.changeOrigin = b.changeOrigin;
        this.pathRewrite = b.pathRewrite == null
                ? Map.of()
                : Map.copyOf(b.pathRewrite);
        this.on = b.on != null ? b.on : OnEvents.EMPTY;
        this.followRedirects = b.followRedirects;
        this.timeout = b.timeout;
    }

    public static Builder builder() { return new Builder(); }

    // Builder

    public static final class Builder {
        private String target;
        private boolean changeOrigin = false;
        private Map<String, String> pathRewrite     = null;
        private OnEvents on = null;
        private boolean followRedirects = false;
        private long timeout = 30_000;

        /** Backend server URL. Required. */
        public Builder target(String target) {
            this.target = target;
            return this;
        }

        /** Rewrite the {@code Host} header to match the target. Default: {@code false}. */
        public Builder changeOrigin(boolean changeOrigin) {
            this.changeOrigin = changeOrigin;
            return this;
        }

        /**
         * Add a single path rewrite rule.
         * @param pattern     regex pattern to match against the request path
         * @param replacement replacement string (supports backreferences: {@code $1})
         */
        public Builder pathRewrite(String pattern, String replacement) {
            if (this.pathRewrite == null) this.pathRewrite = new LinkedHashMap<>();
            this.pathRewrite.put(pattern, replacement);
            return this;
        }

        /** Provide a full path rewrite map (insertion-ordered, first match wins). */
        public Builder pathRewrite(Map<String, String> rules) {
            this.pathRewrite = new LinkedHashMap<>(rules);
            return this;
        }

        /** Register proxy event callbacks. */
        public Builder on(OnEvents on) {
            this.on = on;
            return this;
        }

        /** Follow HTTP redirects from the backend. Default: {@code false}. */
        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        /** Backend connection timeout in milliseconds. Default: {@code 30000}. */
        public Builder timeout(long millis) {
            this.timeout = millis;
            return this;
        }

        public ProxyOptions build() {
            if (target == null || target.isBlank())
                throw new IllegalArgumentException("ProxyOptions.target is required");
            return new ProxyOptions(this);
        }
    }

    // OnEvents

    /**
     * Event callback hooks — mirrors the {@code on} object in http-proxy-middleware.
     *
     * <h3>Callbacks</h3>
     * <ul>
     *   <li>{@link #error} — called when the proxy request fails (connection refused, timeout, etc.)</li>
     *   <li>{@link #proxyReq} — called before the upstream request is sent; use to add/modify headers</li>
     *   <li>{@link #proxyRes} — called after the upstream response is received; use to inspect/modify status</li>
     * </ul>
     */
    public static final class OnEvents {

        static final OnEvents EMPTY = new OnEvents(null, null, null);

        /**
         * Error handler — called when the proxy request fails.
         * Arguments: {@code (Throwable error, Request req, Response res)}.
         * If not set, the middleware calls {@code next.error(err)}.
         */
        public final TriConsumer<Throwable, Request, Response> error;

        /**
         * Pre-request hook — called before the upstream request is sent.
         * Arguments: {@code (HttpRequest.Builder proxyReqBuilder, Request originalReq)}.
         * Use this to add headers, override the method, etc.
         */
        public final BiConsumer<HttpRequest.Builder, Request> proxyReq;

        /**
         * Post-response hook — called after the upstream response arrives but before
         * it is forwarded to the client.
         * Arguments: {@code (HttpResponse<byte[]> proxyRes, Request req, Response res)}.
         * Use this to inspect status, log, or modify response headers.
         */
        public final TriConsumer<HttpResponse<byte[]>, Request, Response> proxyRes;

        private OnEvents(TriConsumer<Throwable, Request, Response> error,
                         BiConsumer<HttpRequest.Builder, Request> proxyReq,
                         TriConsumer<HttpResponse<byte[]>, Request, Response> proxyRes) {
            this.error    = error;
            this.proxyReq = proxyReq;
            this.proxyRes = proxyRes;
        }

        public static OnEvents.Builder builder() { return new OnEvents.Builder(); }

        public static final class Builder {
            private TriConsumer<Throwable, Request, Response>             error    = null;
            private BiConsumer<HttpRequest.Builder, Request>              proxyReq = null;
            private TriConsumer<HttpResponse<byte[]>, Request, Response>  proxyRes = null;

            public Builder error(TriConsumer<Throwable, Request, Response> error) {
                this.error = error;
                return this;
            }

            public Builder proxyReq(BiConsumer<HttpRequest.Builder, Request> proxyReq) {
                this.proxyReq = proxyReq;
                return this;
            }

            public Builder proxyRes(TriConsumer<HttpResponse<byte[]>, Request, Response> proxyRes) {
                this.proxyRes = proxyRes;
                return this;
            }

            public OnEvents build() {
                return new OnEvents(error, proxyReq, proxyRes);
            }
        }
    }

    // TriConsumer

    /** Three-argument consumer (Java doesn't have one built-in). */
    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
