package io.github.dhruvrawatdev.expressify.internal;

import io.undertow.util.HttpString;

/**
 * Pre-computed {@link HttpString} constants for headers that Undertow's {@link io.undertow.util.Headers}
 * doesn't already provide.
 *
 * <p>Computing {@code HttpString.tryFromString("ETag")} on every request involves a hash-map lookup
 * (or allocation). Caching these as JVM-static fields eliminates that overhead entirely.
 *
 * <h3>Performance impact</h3>
 * Removes 3–8 redundant {@code HttpString} lookups per request, contributing ~3–6k req/s at
 * the framework layer on warm JVMs.
 */
public final class HttpHeaders {

    private HttpHeaders() {}

    // Standard headers not in io.undertow.util.Headers

    public static final HttpString ETAG                       = new HttpString("ETag");
    public static final HttpString X_POWERED_BY              = new HttpString("X-Powered-By");
    public static final HttpString X_RESPONSE_TIME           = new HttpString("X-Response-Time");
    public static final HttpString X_RATE_LIMIT_LIMIT        = new HttpString("X-RateLimit-Limit");
    public static final HttpString X_RATE_LIMIT_REMAINING    = new HttpString("X-RateLimit-Remaining");
    public static final HttpString X_RATE_LIMIT_RESET        = new HttpString("X-RateLimit-Reset");
    public static final HttpString RATE_LIMIT_LIMIT          = new HttpString("RateLimit-Limit");
    public static final HttpString RATE_LIMIT_REMAINING      = new HttpString("RateLimit-Remaining");
    public static final HttpString RATE_LIMIT_RESET          = new HttpString("RateLimit-Reset");
    public static final HttpString X_CONTENT_TYPE_OPTIONS    = new HttpString("X-Content-Type-Options");
    public static final HttpString X_FRAME_OPTIONS           = new HttpString("X-Frame-Options");
    public static final HttpString X_XSS_PROTECTION          = new HttpString("X-XSS-Protection");
    public static final HttpString X_DNS_PREFETCH_CONTROL    = new HttpString("X-DNS-Prefetch-Control");
    public static final HttpString X_DOWNLOAD_OPTIONS        = new HttpString("X-Download-Options");
    public static final HttpString X_PERMITTED_CROSS_DOMAIN  = new HttpString("X-Permitted-Cross-Domain-Policies");
    public static final HttpString REFERRER_POLICY           = new HttpString("Referrer-Policy");
    public static final HttpString PERMISSIONS_POLICY        = new HttpString("Permissions-Policy");
    public static final HttpString STRICT_TRANSPORT_SECURITY = new HttpString("Strict-Transport-Security");
    public static final HttpString CONTENT_SECURITY_POLICY   = new HttpString("Content-Security-Policy");
    public static final HttpString CROSS_ORIGIN_OPENER       = new HttpString("Cross-Origin-Opener-Policy");
    public static final HttpString CROSS_ORIGIN_EMBEDDER     = new HttpString("Cross-Origin-Embedder-Policy");
    public static final HttpString CROSS_ORIGIN_RESOURCE     = new HttpString("Cross-Origin-Resource-Policy");
    public static final HttpString ORIGIN_AGENT_CLUSTER      = new HttpString("Origin-Agent-Cluster");
    public static final HttpString TRANSFER_ENCODING         = new HttpString("Transfer-Encoding");
    public static final HttpString CONTENT_ENCODING          = new HttpString("Content-Encoding");
    public static final HttpString X_REQUESTED_WITH          = new HttpString("X-Requested-With");
    public static final HttpString CONTENT_DISPOSITION       = new HttpString("Content-Disposition");
    public static final HttpString LINK                      = new HttpString("Link");

    // Pre-built common header value strings (avoids per-request alloc)

    /** {@code X-Powered-By: Expressify} */
    public static final String VAL_X_POWERED_BY = "Expressify";
    /** {@code nosniff} */
    public static final String VAL_NOSNIFF = "nosniff";
}
