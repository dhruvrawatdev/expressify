package io.github.dhruvrawatdev.expressify.middleware.morgan;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.io.PrintStream;
import java.util.function.BiPredicate;

/**
 * Configuration options for the Morgan HTTP request logger.
 */
public class MorganOptions {

    private final PrintStream stream;
    private final BiPredicate<Request, Response> skip;
    private final boolean immediate;

    private MorganOptions(Builder b) {
        this.stream = b.stream;
        this.skip = b.skip;
        this.immediate = b.immediate;
    }

    public static MorganOptions defaults() {
        return new Builder().build();
    }

    public PrintStream getStream() { return stream; }
    public BiPredicate<Request, Response> getSkip() { return skip; }
    public boolean isImmediate() { return immediate; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private PrintStream stream = System.out;
        private BiPredicate<Request, Response> skip = null;
        private boolean immediate = false;

        /**
         * Output stream to write log lines to. Default: {@code System.out}.
         *
         * @param stream target print stream (e.g., {@code System.err} for error-only logging)
         * @return this builder
         */
        public Builder stream(PrintStream stream) { this.stream = stream;    return this; }

        /**
         * Predicate that suppresses logging when it returns {@code true}.
         *
         * <pre>{@code
         * // Log only 4xx/5xx responses
         * .skip((req, res) -> res.getStatus() < 400)
         * }</pre>
         *
         * @param skip predicate receiving the completed request and response
         * @return this builder
         */
        public Builder skip(BiPredicate<Request, Response> skip) { this.skip = skip; return this; }

        /**
         * When {@code true}, log the request immediately on arrival before the response is
         * available. Useful for timing long requests. Default: {@code false}.
         *
         * @param immediate {@code true} to log on request start instead of response end
         * @return this builder
         */
        public Builder immediate(boolean immediate) { this.immediate = immediate; return this; }

        public MorganOptions build() { return new MorganOptions(this); }
    }
}
