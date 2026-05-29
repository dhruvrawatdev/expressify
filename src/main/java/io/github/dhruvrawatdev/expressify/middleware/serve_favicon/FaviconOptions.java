package io.github.dhruvrawatdev.expressify.middleware.serve_favicon;

/**
 * Configuration options for {@link ServeFavicon} middleware.
 *
 * <pre>{@code
 * app.use(ServeFavicon.use("public/favicon.ico", FaviconOptions.builder()
 *     .maxAge(2592000)  // 30-day browser cache
 *     .build()));
 * }</pre>
 */
public final class FaviconOptions {

    private final int maxAge;

    private FaviconOptions(Builder b) {
        this.maxAge = b.maxAge;
    }

    /**
     * Browser cache duration in seconds, sent via {@code Cache-Control: public, max-age=N}.
     * Default: {@code 86400} (1 day).
     */
    public int getMaxAge() { return maxAge; }

    /** Create a builder for {@link FaviconOptions}. */
    public static Builder builder() { return new Builder(); }

    /** Return the default options (1-day cache). */
    public static FaviconOptions defaults() { return builder().build(); }

    /** Builder for {@link FaviconOptions}. */
    public static final class Builder {

        private int maxAge = 86400;

        /**
         * Set the browser cache duration in seconds.
         *
         * <p>Sent as {@code Cache-Control: public, max-age=N}. Common values:
         * <ul>
         *   <li>{@code 86400} — 1 day (default)</li>
         *   <li>{@code 2592000} — 30 days</li>
         *   <li>{@code 31536000} — 1 year</li>
         *   <li>{@code 0} — no cache</li>
         * </ul>
         *
         * @param seconds cache duration; clamped to {@code >= 0}
         * @return this builder
         */
        public Builder maxAge(int seconds) {
            this.maxAge = Math.max(0, seconds);
            return this;
        }

        /** Build the {@link FaviconOptions}. */
        public FaviconOptions build() { return new FaviconOptions(this); }
    }
}
