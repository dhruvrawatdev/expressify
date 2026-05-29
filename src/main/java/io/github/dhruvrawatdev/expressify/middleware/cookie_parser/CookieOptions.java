package io.github.dhruvrawatdev.expressify.middleware.cookie_parser;

/**
 * Options for setting HTTP cookies — mirrors the Express.js cookie options object.
 * Used with {@code res.cookie(name, value, options)} and {@code res.clearCookie()}.
 */
public class CookieOptions {

    private final boolean signed;
    private final String  secret;
    private final boolean httpOnly;
    private final boolean secure;
    private final String  path;
    private final String  domain;
    private final int     maxAge;
    private final String  sameSite;
    private final String  expires;

    protected CookieOptions(Builder b) {
        this.signed = b.signed;
        this.secret = b.secret;
        this.httpOnly = b.httpOnly;
        this.secure = b.secure;
        this.path = b.path;
        this.domain = b.domain;
        this.maxAge = b.maxAge;
        this.sameSite = b.sameSite;
        this.expires = b.expires;
    }

    public boolean isSigned() { return signed; }
    public String getSecret() { return secret; }
    public boolean isHttpOnly() { return httpOnly; }
    public boolean isSecure() { return secure; }
    public String getPath() { return path; }
    public String getDomain() { return domain; }
    public int getMaxAge() { return maxAge; }
    public String getSameSite() { return sameSite; }
    public String getExpires() { return expires; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean signed = false;
        private String secret = null;
        private boolean httpOnly = true;
        private boolean secure = false;
        private String path = "/";
        private String domain = null;
        private int maxAge = -1;
        private String sameSite = null;
        private String expires = null;

        public Builder signed(boolean v) { this.signed = v; return this; }
        public Builder secret(String v) { this.secret = v; return this; }
        public Builder httpOnly(boolean v) { this.httpOnly  = v; return this; }
        public Builder secure(boolean v) { this.secure = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder domain(String v) { this.domain = v; return this; }
        /** Max age in seconds (positive) or 0 to expire immediately. */
        public Builder maxAge(int v) { this.maxAge = v; return this; }
        /** "Strict", "Lax", or "None". */
        public Builder sameSite(String v) { this.sameSite = v; return this; }
        /** RFC 1123 date string. Prefer maxAge when possible. */
        public Builder expires(String v) { this.expires = v; return this; }

        public CookieOptions build() { return new CookieOptions(this); }
    }
}
