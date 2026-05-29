package io.github.dhruvrawatdev.expressify.middleware.serve_static;

/**
 * Options for {@link ServeStatic} — mirrors express.static() options.
 */
public class StaticOptions {

    private final int maxAge;
    private final String index;
    private final String dotfiles;
    private final boolean etag;
    private final boolean lastModified;
    private final boolean redirect;

    private StaticOptions(Builder b) {
        this.maxAge = b.maxAge;
        this.index = b.index;
        this.dotfiles = b.dotfiles;
        this.etag = b.etag;
        this.lastModified = b.lastModified;
        this.redirect = b.redirect;
    }

    public int getMaxAge() { return maxAge; }
    public String getIndex() { return index; }
    public String getDotfiles() { return dotfiles; }
    public boolean isEtag() { return etag; }
    public boolean isLastModified() { return lastModified; }
    public boolean isRedirect() { return redirect; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int maxAge = 0;
        private String index = "index.html";
        private String dotfiles = "ignore";  // "allow", "deny", "ignore"
        private boolean etag = true;
        private boolean lastModified = true;
        private boolean redirect = true;

        /**
         * Browser cache {@code max-age} in seconds written to the {@code Cache-Control} header.
         * Default: {@code 0} (no caching). Use {@code 86400} for 1-day caching.
         *
         * @param seconds max-age in seconds
         * @return this builder
         */
        public Builder maxAge(int seconds) { this.maxAge = seconds; return this; }

        /**
         * Filename to serve when a directory is requested. Default: {@code "index.html"}.
         * Pass {@code null} or a blank string to disable directory index serving.
         *
         * @param file index filename (e.g., {@code "index.html"})
         * @return this builder
         */
        public Builder index(String file) { this.index = file; return this; }

        /**
         * Policy for requests that resolve to files or directories whose names start with {@code "."}.
         * <ul>
         *   <li>{@code "ignore"} (default) — pass the request to the next handler</li>
         *   <li>{@code "allow"} — serve dotfiles normally</li>
         *   <li>{@code "deny"} — respond with {@code 403 Forbidden}</li>
         * </ul>
         *
         * @param policy one of {@code "allow"}, {@code "deny"}, or {@code "ignore"}
         * @return this builder
         */
        public Builder dotfiles(String policy) { this.dotfiles = policy; return this; }

        /**
         * Generate and validate {@code ETag} headers. Default: {@code true}.
         *
         * @param v {@code false} to disable ETag generation
         * @return this builder
         */
        public Builder etag(boolean v) { this.etag = v; return this; }

        /**
         * Emit and honour {@code Last-Modified} / {@code If-Modified-Since} headers.
         * Default: {@code true}.
         *
         * @param v {@code false} to disable last-modified support
         * @return this builder
         */
        public Builder lastModified(boolean v) { this.lastModified = v; return this; }

        /**
         * Redirect directory paths without a trailing slash by sending {@code 301}.
         * Default: {@code true}.
         *
         * @param v {@code false} to disable directory redirect
         * @return this builder
         */
        public Builder redirect(boolean v) { this.redirect = v; return this; }

        public StaticOptions build() { return new StaticOptions(this); }
    }
}
