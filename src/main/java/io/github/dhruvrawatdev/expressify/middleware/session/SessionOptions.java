package io.github.dhruvrawatdev.expressify.middleware.session;

/**
 * Options for {@link SessionMiddleware} — mirrors express-session options.
 */
public class SessionOptions {

    private final String secret;
    private final String name;
    private final int maxAge;
    private final boolean httpOnly;
    private final boolean secure;
    private final String sameSite;
    private final boolean rolling;
    private final boolean saveUninitialized;
    private final boolean resave;

    private SessionOptions(Builder b) {
        this.secret = b.secret;
        this.name = b.name;
        this.maxAge = b.maxAge;
        this.httpOnly = b.httpOnly;
        this.secure = b.secure;
        this.sameSite = b.sameSite;
        this.rolling = b.rolling;
        this.saveUninitialized = b.saveUninitialized;
        this.resave = b.resave;
    }

    public String getSecret() { return secret; }
    public String getName() { return name; }
    public int getMaxAge() { return maxAge; }
    public boolean isHttpOnly() { return httpOnly; }
    public boolean isSecure() { return secure; }
    public String  getSameSite() { return sameSite; }
    public boolean isRolling() { return rolling; }
    public boolean isSaveUninitialized() { return saveUninitialized; }
    public boolean isResave() { return resave; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String  secret = "changeme";
        private String  name = "connect.sid";
        private int     maxAge = 86400;   // 24 hours in seconds
        private boolean httpOnly = true;
        private boolean secure = false;
        private String  sameSite = "Lax";
        private boolean rolling = false;
        private boolean saveUninitialized = false;
        private boolean resave = false;

        /**
         * HMAC-SHA256 secret used to sign the session ID stored in the browser cookie.
         * Default: {@code "changeme"} — <strong>must be changed in production</strong>.
         *
         * @param v secret string; use a long random value in production
         * @return this builder
         */
        public Builder secret(String v) { this.secret = v; return this; }

        /**
         * Name of the session cookie sent to and read from the browser.
         * Default: {@code "connect.sid"}.
         *
         * @param v cookie name
         * @return this builder
         */
        public Builder name(String v) { this.name = v; return this; }

        /**
         * Session idle timeout in seconds. The Caffeine store evicts sessions after this
         * many seconds of inactivity. Default: {@code 86400} (24 hours).
         *
         * @param seconds idle TTL in seconds
         * @return this builder
         */
        public Builder maxAge(int seconds) { this.maxAge = seconds; return this; }

        /**
         * Mark the session cookie as {@code HttpOnly} (not accessible from JavaScript).
         * Default: {@code true}.
         *
         * @param v {@code true} to set the HttpOnly flag
         * @return this builder
         */
        public Builder httpOnly(boolean v) { this.httpOnly = v; return this; }

        /**
         * Mark the session cookie as {@code Secure} (sent only over HTTPS).
         * Default: {@code false}. Set to {@code true} behind a TLS reverse proxy.
         *
         * @param v {@code true} to set the Secure flag
         * @return this builder
         */
        public Builder secure(boolean v) { this.secure = v; return this; }

        /**
         * Value of the {@code SameSite} cookie attribute.
         * Accepted values: {@code "Strict"}, {@code "Lax"}, {@code "None"}. Default: {@code "Lax"}.
         *
         * @param v SameSite policy string
         * @return this builder
         */
        public Builder sameSite(String v) { this.sameSite = v; return this; }

        /**
         * Reset the session TTL on every request so the session stays alive while active.
         * Default: {@code false}.
         *
         * @param v {@code true} to refresh the TTL on every request
         * @return this builder
         */
        public Builder rolling(boolean v) { this.rolling = v; return this; }

        /**
         * Persist sessions that were never modified during the request.
         * Default: {@code false} (only save sessions that were written to).
         *
         * @param v {@code true} to save all new sessions regardless of modification
         * @return this builder
         */
        public Builder saveUninitialized(boolean v){ this.saveUninitialized = v;  return this; }

        /**
         * Re-save the session to the store on every request even if it was not modified.
         * Default: {@code false}.
         *
         * @param v {@code true} to always persist the session after each request
         * @return this builder
         */
        public Builder resave(boolean v) { this.resave = v; return this; }

        public SessionOptions build() { return new SessionOptions(this); }
    }
}
