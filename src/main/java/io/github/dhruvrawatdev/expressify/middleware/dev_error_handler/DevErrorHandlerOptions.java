package io.github.dhruvrawatdev.expressify.middleware.dev_error_handler;

/**
 * Configuration options for {@link DevErrorHandler} middleware.
 *
 * <pre>{@code
 * app.error(DevErrorHandler.create(DevErrorHandlerOptions.builder()
 *     .log(false)  // suppress error logging
 *     .build()));
 * }</pre>
 */
public final class DevErrorHandlerOptions {

    private final boolean log;

    private DevErrorHandlerOptions(Builder b) {
        this.log = b.log;
    }

    /**
     * Whether to log errors to {@code System.err}.
     * Default: {@code true}.
     */
    public boolean isLog() { return log; }

    /** Create a builder for {@link DevErrorHandlerOptions}. */
    public static Builder builder() { return new Builder(); }

    /** Return the default options (logging enabled). */
    public static DevErrorHandlerOptions defaults() { return builder().build(); }

    /** Builder for {@link DevErrorHandlerOptions}. */
    public static final class Builder {

        private boolean log = true;

        /**
         * Set whether to print errors to {@code System.err}.
         *
         * @param log {@code true} to log (default), {@code false} to suppress
         * @return this builder
         */
        public Builder log(boolean log) { this.log = log; return this; }

        /** Build the {@link DevErrorHandlerOptions}. */
        public DevErrorHandlerOptions build() { return new DevErrorHandlerOptions(this); }
    }
}
