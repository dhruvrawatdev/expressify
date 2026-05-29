package io.github.dhruvrawatdev.expressify.middleware.cors;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Configuration for {@link Cors} middleware — mirrors the Express cors package options.
 */
public class CorsOptions {

    @FunctionalInterface
    public interface OriginCallback {
        /** Called with the request origin and a callback(allowed) to decide dynamically. */
        void check(String origin, BiConsumer<Exception, Boolean> callback);
    }

    private final Set<String> origins;
    private final OriginCallback originCallback;
    private final List<String> methods;
    private final List<String> allowHeaders;
    private final List<String> exposedHeaders;
    private final boolean credentials;
    private final int maxAge;
    private final boolean preflightContinue;
    private final int optionsSuccessStatus;

    private CorsOptions(Builder b) {
        this.origins = Collections.unmodifiableSet(b.origins);
        this.originCallback = b.originCallback;
        this.methods = Collections.unmodifiableList(b.methods);
        this.allowHeaders = Collections.unmodifiableList(b.allowHeaders);
        this.exposedHeaders = Collections.unmodifiableList(b.exposedHeaders);
        this.credentials = b.credentials;
        this.maxAge = b.maxAge;
        this.preflightContinue = b.preflightContinue;
        this.optionsSuccessStatus = b.optionsSuccessStatus;
    }

    public Set<String> getOrigins() { return origins; }
    public OriginCallback getOriginCallback() { return originCallback; }
    public List<String> getMethods() { return methods; }
    public List<String> getAllowHeaders() { return allowHeaders; }
    public List<String> getExposedHeaders() { return exposedHeaders; }
    public boolean isCredentials() { return credentials; }
    public int getMaxAge() { return maxAge; }
    public boolean isPreflightContinue() { return preflightContinue; }
    public int getOptionsSuccessStatus() { return optionsSuccessStatus; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Set<String> origins = new LinkedHashSet<>();
        private OriginCallback originCallback = null;
        private final List<String> methods = new ArrayList<>(Arrays.asList("GET","HEAD","PUT","PATCH","POST","DELETE"));
        private final List<String> allowHeaders = new ArrayList<>();
        private final List<String> exposedHeaders  = new ArrayList<>();
        private boolean credentials = false;
        private int maxAge = -1;
        private boolean preflightContinue = false;
        private int optionsSuccessStatus = 204;

        /**
         * Allow a specific origin. Pass {@code "*"} to allow any origin,
         * or an exact URL such as {@code "https://example.com"}.
         * Can be called multiple times to accumulate allowed origins.
         *
         * @param origin {@code "*"} or an exact origin URL
         * @return this builder
         */
        public Builder origin(String origin) { this.origins.add(origin); return this; }

        /**
         * Allow multiple specific origins at once.
         *
         * @param origins one or more origin URLs or {@code "*"}
         * @return this builder
         */
        public Builder origins(String... origins) { this.origins.addAll(Arrays.asList(origins)); return this; }

        /**
         * Dynamic origin resolution via callback — called on every incoming request.
         *
         * <pre>{@code
         * .origin((origin, callback) -> {
         *     boolean allowed = db.isAllowedOrigin(origin);
         *     callback.accept(null, allowed);
         * })
         * }</pre>
         *
         * @param cb callback that receives {@code (origin, (err, allowed) -> {})}
         * @return this builder
         */
        public Builder origin(OriginCallback cb) { this.originCallback = cb; return this; }

        /**
         * Replace the default allowed HTTP methods list.
         * Default: {@code GET, HEAD, PUT, PATCH, POST, DELETE}.
         *
         * @param methods HTTP method names in upper-case
         * @return this builder
         */
        public Builder methods(String... methods) { this.methods.clear(); this.methods.addAll(Arrays.asList(methods)); return this; }

        /**
         * Headers the client is allowed to send. Alias for {@link #allowedHeaders(String...)}.
         *
         * @param headers header names to add to {@code Access-Control-Allow-Headers}
         * @return this builder
         */
        public Builder headers(String... headers) { this.allowHeaders.addAll(Arrays.asList(headers)); return this; }

        /**
         * Headers the client is allowed to send.
         *
         * @param headers header names to add to {@code Access-Control-Allow-Headers}
         * @return this builder
         */
        public Builder allowedHeaders(String... headers){ this.allowHeaders.addAll(Arrays.asList(headers)); return this; }

        /**
         * Headers that browser JavaScript is allowed to read from the response.
         *
         * @param headers header names to add to {@code Access-Control-Expose-Headers}
         * @return this builder
         */
        public Builder exposedHeaders(String... headers){ this.exposedHeaders.addAll(Arrays.asList(headers)); return this; }

        /**
         * Set {@code Access-Control-Allow-Credentials: true} so the browser includes cookies
         * and {@code Authorization} headers in cross-origin requests.
         * Requires a non-wildcard origin.
         *
         * @param v {@code true} to allow credentials
         * @return this builder
         */
        public Builder credentials(boolean v) { this.credentials = v; return this; }

        /**
         * How long (seconds) the browser may cache preflight responses.
         * Sets {@code Access-Control-Max-Age}. Default: {@code -1} (not sent).
         *
         * @param seconds cache duration in seconds; use {@code 86400} for 1 day
         * @return this builder
         */
        public Builder maxAge(int seconds) { this.maxAge = seconds; return this; }

        /**
         * When {@code true}, {@code OPTIONS} preflight requests are passed to the next handler
         * instead of being auto-answered with a {@code 204}. Default: {@code false}.
         *
         * @param v {@code true} to delegate preflight handling to downstream middleware
         * @return this builder
         */
        public Builder preflightContinue(boolean v) { this.preflightContinue = v; return this; }

        /**
         * HTTP status code used for successful {@code OPTIONS} preflight responses.
         * Default: {@code 204}. Use {@code 200} for compatibility with some legacy browsers.
         *
         * @param v HTTP status code (typically {@code 204} or {@code 200})
         * @return this builder
         */
        public Builder optionsSuccessStatus(int v) { this.optionsSuccessStatus = v; return this; }

        public CorsOptions build() { return new CorsOptions(this); }
    }
}
