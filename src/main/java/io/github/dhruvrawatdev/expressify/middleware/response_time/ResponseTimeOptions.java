package io.github.dhruvrawatdev.expressify.middleware.response_time;

/**
 * Configuration options for the ResponseTime middleware.
 */
public class ResponseTimeOptions {

    private final int digits;
    private final String header;
    private final boolean suffix;

    private ResponseTimeOptions(Builder b) {
        this.digits = b.digits;
        this.header = b.header;
        this.suffix = b.suffix;
    }

    public static ResponseTimeOptions defaults() {
        return new Builder().build();
    }

    /** Decimal digits in the response time value (default: 3). */
    public int getDigits() { return digits; }
    /** Header name (default: X-Response-Time). */
    public String getHeader() { return header; }
    /** Whether to append "ms" suffix (default: true). */
    public boolean hasSuffix() { return suffix; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int digits = 3;
        private String header = "X-Response-Time";
        private boolean suffix = true;

        public Builder digits(int digits) { this.digits = digits; return this; }
        public Builder header(String header) { this.header = header; return this; }
        public Builder suffix(boolean suffix) { this.suffix = suffix; return this; }

        public ResponseTimeOptions build() { return new ResponseTimeOptions(this); }
    }
}
