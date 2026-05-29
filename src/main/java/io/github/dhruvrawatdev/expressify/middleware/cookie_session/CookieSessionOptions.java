package io.github.dhruvrawatdev.expressify.middleware.cookie_session;

import java.util.Collections;
import java.util.List;

/**
 * Configuration options for {@link CookieSession} — mirrors the cookie-session npm package.
 */
public class CookieSessionOptions {

    private final String name;
    private final List<String> keys;
    private final boolean httpOnly;
    private final boolean secure;
    private final boolean signed;
    private final boolean overwrite;
    private final int maxAge;
    private final String path;
    private final String domain;
    private final String sameSite;

    private CookieSessionOptions(Builder b) {
        this.name = b.name;
        this.keys = Collections.unmodifiableList(b.keys);
        this.httpOnly = b.httpOnly;
        this.secure = b.secure;
        this.signed = b.signed;
        this.overwrite = b.overwrite;
        this.maxAge = b.maxAge;
        this.path = b.path;
        this.domain = b.domain;
        this.sameSite = b.sameSite;
    }

    public String name() { return name; }
    public List<String> keys() { return keys; }
    public boolean httpOnly() { return httpOnly; }
    public boolean secure() { return secure; }
    public boolean signed() { return signed; }
    public boolean overwrite() { return overwrite; }
    /** Max age in seconds; -1 means session cookie (expires when browser closes). */
    public int maxAge() { return maxAge; }
    public String path() { return path; }
    public String domain() { return domain; }
    public String sameSite() { return sameSite; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name = "session";
        private List<String> keys = Collections.emptyList();
        private boolean httpOnly = true;
        private boolean secure = false;
        private boolean signed = true;
        private boolean overwrite = true;
        private int maxAge = -1;
        private String path = "/";
        private String domain = null;
        private String sameSite = null;

        /**
         * Cookie name used to store the session payload. Default: {@code "session"}.
         *
         * @param v cookie name
         * @return this builder
         */
        public Builder name(String v){ this.name = v; return this; }

        /**
         * Ordered list of HMAC-SHA256 signing keys.
         * The first key signs new cookies; all keys are tried when verifying incoming cookies,
         * enabling zero-downtime key rotation.
         *
         * @param v signing key list; must not be empty when {@code signed = true}
         * @return this builder
         */
        public Builder keys(List<String> v) { this.keys= v; return this; }

        /**
         * Shorthand to set a single signing key.
         * Equivalent to {@code keys(List.of(secret))}.
         *
         * @param v HMAC-SHA256 signing secret
         * @return this builder
         */
        public Builder secret(String v) { this.keys = v == null ? Collections.emptyList() : List.of(v); return this; }

        /**
         * Mark the cookie as {@code HttpOnly} (not accessible from JavaScript). Default: {@code true}.
         *
         * @param v {@code true} to set the HttpOnly flag
         * @return this builder
         */
        public Builder httpOnly(boolean v) { this.httpOnly = v; return this; }

        /**
         * Mark the cookie as {@code Secure} (sent only over HTTPS). Default: {@code false}.
         * Set to {@code true} when deployed behind a TLS reverse proxy.
         *
         * @param v {@code true} to set the Secure flag
         * @return this builder
         */
        public Builder secure(boolean v) { this.secure = v; return this; }

        /**
         * HMAC-sign the cookie value to prevent client tampering. Default: {@code true}.
         * Requires at least one key via {@link #keys} or {@link #secret}.
         *
         * @param v {@code false} to store plain Base64 JSON without a signature
         * @return this builder
         */
        public Builder signed(boolean v) { this.signed = v; return this; }

        /**
         * Allow the response to overwrite cookies set earlier in the same request.
         * Default: {@code true}.
         *
         * @param v {@code false} to preserve cookies set by earlier middleware
         * @return this builder
         */
        public Builder overwrite(boolean v) { this.overwrite = v; return this; }

        /**
         * Cookie max-age in seconds. Default: {@code -1} (session cookie — expires when browser closes).
         * Use {@code 86400} for 1 day.
         *
         * @param v max-age in seconds; {@code -1} for a session cookie
         * @return this builder
         */
        public Builder maxAge(int v) { this.maxAge = v; return this; }

        /**
         * Cookie path scope. Default: {@code "/"} (entire site).
         *
         * @param v URL path prefix for the cookie scope
         * @return this builder
         */
        public Builder path(String v) { this.path = v; return this; }

        /**
         * Cookie domain scope. Default: {@code null} (current host only).
         *
         * @param v domain string; use {@code ".example.com"} to include subdomains
         * @return this builder
         */
        public Builder domain(String v) { this.domain = v; return this; }

        /**
         * SameSite attribute controlling cross-site cookie behaviour.
         * Accepted values: {@code "Strict"}, {@code "Lax"}, {@code "None"}.
         * Default: {@code null} (attribute not set).
         *
         * @param v SameSite policy string
         * @return this builder
         */
        public Builder sameSite(String v) { this.sameSite = v; return this; }

        public CookieSessionOptions build() { return new CookieSessionOptions(this); }
    }
}
