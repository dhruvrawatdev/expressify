package io.github.dhruvrawatdev.expressify.socket;

/**
 * Configuration options for {@link ExpressifyIO}.
 *
 * <pre>{@code
 * ExpressifyIO io = new ExpressifyIO(IoServerOptions.builder()
 *     .path("/socket.io")
 *     .pingInterval(25_000)
 *     .pingTimeout(20_000)
 *     .maxPayload(1_000_000)
 *     .build());
 * }</pre>
 */
public final class IoServerOptions {

    /** URL path prefix the Socket.IO endpoint is mounted at. Default: {@code "/socket.io"}. */
    public final String path;

    /** How often the server sends a ping frame (ms). Default: 25 000. */
    public final long pingInterval;

    /** How long to wait for a pong before disconnecting (ms). Default: 20 000. */
    public final long pingTimeout;

    /**
     * Maximum byte size of a single Engine.IO message payload.
     * Default: 1 000 000 bytes (same as Socket.IO default).
     */
    public final long maxPayload;

    /**
     * How long to wait for a CONNECT packet after the Engine.IO handshake (ms).
     * Default: 45 000.
     */
    public final long connectTimeout;

    private IoServerOptions(Builder b) {
        this.path = b.path;
        this.pingInterval = b.pingInterval;
        this.pingTimeout = b.pingTimeout;
        this.maxPayload = b.maxPayload;
        this.connectTimeout = b.connectTimeout;
    }

    public static Builder builder() { return new Builder(); }

    public static final IoServerOptions DEFAULT = builder().build();

    public static final class Builder {
        private String path = "/socket.io";
        private long pingInterval = 25_000;
        private long pingTimeout = 20_000;
        private long maxPayload = 1_000_000;
        private long connectTimeout = 45_000;

        /**
         * URL path the Socket.IO endpoint is mounted at (default {@code "/socket.io"}).
         * Trailing slashes are stripped automatically.
         *
         * @param path the mount path, e.g. {@code "/socket.io"}
         * @return this builder
         */
        public Builder path(String path) {
            this.path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            return this;
        }

        /**
         * How often the server sends an Engine.IO ping frame (default 25 000 ms).
         *
         * @param ms interval in milliseconds
         * @return this builder
         */
        public Builder pingInterval(long ms) { this.pingInterval   = ms; return this; }

        /**
         * How long to wait for a pong before disconnecting the client (default 20 000 ms).
         *
         * @param ms timeout in milliseconds
         * @return this builder
         */
        public Builder pingTimeout(long ms)  {
            this.pingTimeout = ms;
            return this;
        }

        /**
         * Maximum Engine.IO message payload in bytes (default 1 000 000).
         * Messages larger than this are rejected.
         *
         * @param bytes maximum payload size
         * @return this builder
         */
        public Builder maxPayload(long bytes) {
            this.maxPayload = bytes;
            return this;
        }

        /**
         * How long to wait for the Socket.IO CONNECT packet after the Engine.IO handshake
         * (default 45 000 ms). Currently informational — not enforced by a timer.
         *
         * @param ms timeout in milliseconds
         * @return this builder
         */
        public Builder connectTimeout(long ms) {
            this.connectTimeout = ms;
            return this;
        }

        /**
         * Build the {@link IoServerOptions} instance.
         *
         * @return an immutable options object
         */
        public IoServerOptions build() {
            return new IoServerOptions(this);
        }
    }
}
