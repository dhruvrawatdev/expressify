package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

/**
 * Options for {@link BodyParser#raw()} — mirrors express.raw() options.
 *
 * <pre>{@code
 * app.use(BodyParser.raw(RawOptions.builder()
 *     .type("application/pdf")
 *     .limit("5mb")
 *     .inflate(true)
 *     .build()));
 * }</pre>
 */
public class RawOptions {

    private final boolean inflate;
    private final String limit;
    private final String[]types;
    private final BodyVerifier verify;

    private RawOptions(Builder b) {
        this.inflate = b.inflate;
        this.limit = b.limit;
        this.types = b.types;
        this.verify = b.verify;
    }

    public boolean isInflate() { return inflate; }
    public String getLimit()  { return limit; }
    public String[] getTypes()  { return types; }
    public BodyVerifier getVerify() { return verify; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean inflate = true;
        private String limit = "100kb";
        private String[] types = { "application/octet-stream" };
        private BodyVerifier verify  = null;

        public Builder inflate(boolean v) { this.inflate = v; return this; }
        public Builder limit(String v) { this.limit = v; return this; }
        public Builder type(String v) { this.types = new String[]{ v }; return this; }
        public Builder types(String... v) { this.types = v; return this; }
        public Builder verify(BodyVerifier v) { this.verify = v; return this; }

        public RawOptions build() { return new RawOptions(this); }
    }
}
