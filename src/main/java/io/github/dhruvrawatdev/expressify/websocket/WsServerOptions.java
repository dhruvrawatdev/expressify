package io.github.dhruvrawatdev.expressify.websocket;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.util.Collections;
import java.util.List;

/**
 * Configuration options for a {@link WsServer} endpoint.
 * Mirrors the options object accepted by the Node.js {@code ws.WebSocketServer} constructor.
 *
 * <pre>{@code
 * WsServerOptions opts = WsServerOptions.builder()
 *     .maxPayload(512 * 1024)      // 512 KB
 *     .clientTracking(true)
 *     .autoPong(true)
 *     .verifyClient(info -> isAuthenticated(info.req()))
 *     .build();
 *
 * app.ws("/chat", opts, (ws, req) -> {
 *     ws.onMessage(msg -> ws.send("Echo: " + msg.asText()));
 * });
 * }</pre>
 */
public final class WsServerOptions {

    /** Maximum allowed message payload in bytes. Default: 100 MB (matches Node.js ws default). */
    public final long maxPayload;

    /** Track connected clients in {@link WsServer#clients()}. Default: {@code true}. */
    public final boolean clientTracking;

    /**
     * Automatically send a pong frame in response to every ping.
     * Default: {@code true} — mirrors Node.js ws {@code autoPong} option.
     */
    public final boolean autoPong;

    /**
     * Called before completing the WebSocket handshake.
     * Return {@code false} to reject the connection (sends HTTP 401).
     * Default: {@code null} (all connections accepted).
     */
    public final VerifyClient verifyClient;

    /**
     * Select the negotiated subprotocol when the client offers multiple options.
     * Return the chosen protocol name, or {@code null} to decline all (no subprotocol negotiated).
     * Default: {@code null} (first offered protocol is used if declared in {@code subprotocols}).
     */
    public final ProtocolSelector handleProtocols;

    /**
     * Ordered list of subprotocols this server supports.
     * Undertow will select the first match from what the client offers.
     * Default: empty list (no subprotocol restriction).
     */
    public final List<String> subprotocols;

    private WsServerOptions(Builder b) {
        this.maxPayload = b.maxPayload;
        this.clientTracking = b.clientTracking;
        this.autoPong = b.autoPong;
        this.verifyClient = b.verifyClient;
        this.handleProtocols = b.handleProtocols;
        this.subprotocols = Collections.unmodifiableList(b.subprotocols);
    }

    public static Builder builder() { return new Builder(); }

    // Functional interfaces

    /**
     * Callback invoked before the WebSocket handshake completes.
     * Return {@code false} to reject the connection with HTTP 401.
     *
     * <p>Mirrors the synchronous form of Node.js ws {@code verifyClient}:
     * {@code verifyClient: (info) => boolean}.
     */
    @FunctionalInterface
    public interface VerifyClient {
        boolean verify(WsClientInfo info);
    }

    /**
     * Callback to dynamically select the negotiated WebSocket subprotocol.
     * Return the chosen name from the offered list, or {@code null} to skip negotiation.
     *
     * <p>Mirrors the Node.js ws {@code handleProtocols} option:
     * {@code handleProtocols: (protocols, request) => string | false}.
     */
    @FunctionalInterface
    public interface ProtocolSelector {
        String select(List<String> offeredProtocols, Request upgradeRequest);
    }

    // Builder

    public static final class Builder {
        private long maxPayload = 100L * 1024 * 1024;
        private boolean clientTracking = true;
        private boolean autoPong = true;
        private VerifyClient verifyClient = null;
        private ProtocolSelector handleProtocols = null;
        private List<String> subprotocols = List.of();

        /**
         * Maximum payload in bytes (default 100 MB).
         * Messages larger than this limit cause the connection to be closed with code 1009.
         */
        public Builder maxPayload(long bytes) {
            this.maxPayload = bytes;
            return this;
        }

        /**
         * Enable or disable client tracking.
         * When {@code true}, {@link WsServer#clients()} returns the live connected-client set.
         */
        public Builder clientTracking(boolean track) {
            this.clientTracking = track;
            return this;
        }

        /** Auto-send pong on ping (default {@code true}). */
        public Builder autoPong(boolean auto) {
            this.autoPong = auto;
            return this;
        }

        /**
         * Connection guard — return {@code false} to reject with HTTP 401.
         *
         * <pre>{@code
         * .verifyClient(info -> {
         *     String token = info.req().get("Authorization");
         *     return validateToken(token);
         * })
         * }</pre>
         */
        public Builder verifyClient(VerifyClient v) {
            this.verifyClient = v;
            return this;
        }

        /**
         * Dynamic subprotocol selection.
         *
         * <pre>{@code
         * .handleProtocols((protocols, req) ->
         *     protocols.contains("chat.v2") ? "chat.v2" : protocols.get(0))
         * }</pre>
         */
        public Builder handleProtocols(ProtocolSelector s) {
            this.handleProtocols = s;
            return this;
        }

        /**
         * Declare subprotocols this server supports (e.g. {@code "chat", "json"}).
         * Undertow selects the first offered protocol that appears in this list.
         */
        public Builder subprotocols(String... protocols) {
            this.subprotocols = List.of(protocols);
            return this;
        }

        public WsServerOptions build() { return new WsServerOptions(this); }
    }

    // Shared default instance — avoids repeated allocations
    public static final WsServerOptions DEFAULT = builder().build();
}
