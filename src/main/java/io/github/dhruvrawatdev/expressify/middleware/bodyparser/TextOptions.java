package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

/**
 * Options for {@link BodyParser#text()} — mirrors express.text() options.
 *
 * <pre>{@code
 * app.use(BodyParser.text(TextOptions.builder()
 *     .type("text/csv")
 *     .defaultCharset("iso-8859-1")
 *     .limit("500kb")
 *     .inflate(true)
 *     .build()));
 * }</pre>
 */
public class TextOptions {

    private final String defaultCharset;
    private final boolean inflate;
    private final String limit;
    private final String[] types;
    private final BodyVerifier verify;

    private TextOptions(Builder b) {
        this.defaultCharset = b.defaultCharset;
        this.inflate = b.inflate;
        this.limit = b.limit;
        this.types = b.types;
        this.verify = b.verify;
    }

    public String getDefaultCharset() { return defaultCharset; }
    public boolean isInflate() { return inflate; }
    public String getLimit() { return limit; }
    public String[] getTypes() { return types; }
    public BodyVerifier getVerify() { return verify; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String defaultCharset = "utf-8";
        private boolean inflate = true;
        private String limit = "100kb";
        private String[] types = { "text/plain" };
        private BodyVerifier verify = null;

        public Builder defaultCharset(String v) { this.defaultCharset = v; return this; }
        public Builder inflate(boolean v) { this.inflate = v; return this; }
        public Builder limit(String v) { this.limit = v; return this; }
        public Builder type(String v) { this.types = new String[]{ v }; return this; }
        public Builder types(String... v) { this.types = v; return this; }
        public Builder verify(BodyVerifier v) { this.verify = v; return this; }

        public TextOptions build() { return new TextOptions(this); }
    }
}
