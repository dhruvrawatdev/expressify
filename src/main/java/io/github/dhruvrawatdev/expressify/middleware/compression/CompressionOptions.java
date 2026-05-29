package io.github.dhruvrawatdev.expressify.middleware.compression;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Options for {@link Compression} middleware — mirrors the compression npm package.
 */
public class CompressionOptions {

    @FunctionalInterface
    public interface CompressionFilter {
        boolean shouldCompress(Request req, Response res);
    }

    private final int threshold;
    private final int level;
    private final CompressionFilter filter;
    private final boolean brotli;

    private CompressionOptions(Builder b) {
        this.threshold = b.threshold;
        this.level = b.level;
        this.filter = b.filter;
        this.brotli = b.brotli;
    }

    public int getThreshold() { return threshold; }
    public int getLevel() { return level; }
    public CompressionFilter getFilter() { return filter; }
    public boolean isBrotli() { return brotli; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int threshold = 1024;  // 1 KB
        private int level = 6;
        private CompressionFilter filter    = null;
        private boolean brotli = false;

        /**
         * Minimum response body size in bytes below which compression is skipped.
         * Default: {@code 1024} (1 KB).
         *
         * @param bytes minimum byte count; responses smaller than this are sent uncompressed
         * @return this builder
         */
        public Builder threshold(int bytes) { this.threshold = bytes; return this; }

        /**
         * gzip / deflate compression level from {@code 1} (fastest) to {@code 9} (best ratio).
         * Default: {@code 6}.
         *
         * @param v compression level 1–9
         * @return this builder
         */
        public Builder level(int v) { this.level = v; return this; }

        /**
         * Custom predicate called after content-type and threshold checks.
         * Return {@code false} to skip compression for a specific request/response pair.
         *
         * <pre>{@code
         * .filter((req, res) -> !req.path().startsWith("/stream"))
         * }</pre>
         *
         * @param f predicate; {@code (req, res) -> true} means compress
         * @return this builder
         */
        public Builder filter(CompressionFilter f) { this.filter = f; return this; }

        /**
         * Enable brotli compression when the client sends {@code Accept-Encoding: br}.
         * Currently reserved — not yet implemented.
         *
         * @param v {@code true} to opt into future brotli support
         * @return this builder
         */
        public Builder brotli(boolean v) { this.brotli = v; return this; }

        public CompressionOptions build() { return new CompressionOptions(this); }
    }
}
