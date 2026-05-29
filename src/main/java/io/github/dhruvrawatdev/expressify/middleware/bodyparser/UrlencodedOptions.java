package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

/**
 * Options for {@link BodyParser#urlencoded()} — mirrors express.urlencoded() options.
 *
 * <pre>{@code
 * app.use(BodyParser.urlencoded(UrlencodedOptions.builder()
 *     .extended(true)
 *     .limit("1mb")
 *     .build()));
 * }</pre>
 */
public class UrlencodedOptions {

    private final boolean extended;
    private final boolean inflate;
    private final String limit;
    private final int parameterLimit;
    private final String type;
    private final BodyVerifier verify;

    private UrlencodedOptions(Builder b) {
        this.extended = b.extended;
        this.inflate = b.inflate;
        this.limit = b.limit;
        this.parameterLimit = b.parameterLimit;
        this.type = b.type;
        this.verify = b.verify;
    }

    public boolean isExtended() { return extended; }
    public boolean isInflate() { return inflate; }
    public String getLimit() { return limit; }
    public int getParameterLimit(){ return parameterLimit; }
    public String getType() { return type; }
    public BodyVerifier getVerify() { return verify; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean extended = true;
        private boolean inflate = true;
        private String limit = "100kb";
        private int parameterLimit = 1000;
        private String type = "application/x-www-form-urlencoded";
        private BodyVerifier verify = null;

        /**
         * Parse nested objects and arrays using bracket notation ({@code user[name]=Alice}).
         * Default: {@code true}. When {@code false}, only flat key-value pairs are parsed.
         *
         * @param v {@code true} for extended (nested) parsing, {@code false} for simple flat parsing
         * @return this builder
         */
        public Builder extended(boolean v) { this.extended = v; return this; }

        /**
         * Accept {@code gzip} / {@code deflate} compressed bodies. Default: {@code true}.
         *
         * @param v {@code false} to reject compressed bodies with {@code 415}
         * @return this builder
         */
        public Builder inflate(boolean v) { this.inflate = v; return this; }

        /**
         * Maximum body size as a string with optional unit suffix.
         * Supports {@code "kb"}, {@code "mb"}, {@code "gb"}, and plain bytes.
         * Default: {@code "100kb"}.
         *
         * @param v size string such as {@code "500kb"}, {@code "1mb"}, {@code "2097152"}
         * @return this builder
         */
        public Builder limit(String v) { this.limit = v; return this; }

        /**
         * Maximum number of URL-encoded parameters allowed per request.
         * Requests exceeding this limit receive a {@code 413} response. Default: {@code 1000}.
         *
         * @param n maximum parameter count
         * @return this builder
         */
        public Builder parameterLimit(int n) { this.parameterLimit = n; return this; }

        /**
         * Content-Type value that triggers URL-encoded parsing.
         * Default: {@code "application/x-www-form-urlencoded"}.
         *
         * @param v media type string
         * @return this builder
         */
        public Builder type(String v) { this.type = v; return this; }

        /**
         * Optional body verifier called after the body is read and decompressed.
         * Throw an exception inside the verifier to reject the request.
         *
         * @param v verifier implementation; {@code (req, res, buf, encoding) -> void}
         * @return this builder
         */
        public Builder verify(BodyVerifier v) { this.verify = v; return this; }

        public UrlencodedOptions build() { return new UrlencodedOptions(this); }
    }
}
