package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

/** Options for {@link BodyParser#json()} — mirrors express.json() options. */
public class JsonOptions {

    private final boolean inflate;
    private final long limit;
    private final String type;
    private final boolean strict;

    private JsonOptions(Builder b) {
        this.inflate = b.inflate;
        this.limit = b.limit;
        this.type = b.type;
        this.strict = b.strict;
    }

    public boolean isInflate() { return inflate; }
    public long getLimit() { return limit; }
    public String getType() { return type; }
    public boolean isStrict() { return strict; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean inflate = true;
        private long limit = 100 * 1024L; // 100 KB
        private String type  = "application/json";
        private boolean strict = true;

        /**
         * Accept {@code gzip} / {@code deflate} compressed request bodies.
         * Default: {@code true}.
         *
         * @param v {@code false} to reject compressed bodies with {@code 415}
         * @return this builder
         */
        public Builder inflate(boolean v) { this.inflate = v; return this; }

        /**
         * Maximum allowed body size in bytes. Default: {@code 102400} (100 KB).
         * Requests exceeding this limit receive a {@code 413 Payload Too Large} response.
         *
         * @param bytes maximum body size in bytes
         * @return this builder
         */
        public Builder limit(long bytes) { this.limit = bytes; return this; }

        /**
         * Content-Type pattern that triggers JSON parsing. Default: {@code "application/json"}.
         *
         * @param t media type string (e.g., {@code "application/json"} or {@code "text/plain"})
         * @return this builder
         */
        public Builder type(String t) { this.type = t;   return this; }

        /**
         * Restrict parsing to JSON objects and arrays at the top level.
         * Rejects bare strings, numbers, and booleans when {@code true}. Default: {@code true}.
         *
         * @param v {@code false} to accept any valid JSON value
         * @return this builder
         */
        public Builder strict(boolean v) { this.strict = v; return this; }

        public JsonOptions build() { return new JsonOptions(this); }
    }
}
