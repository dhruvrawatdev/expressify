package io.github.dhruvrawatdev.expressify.socket;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata gathered at connection time — available via {@link IoSocket#handshake()}.
 *
 * <p>Mirrors the Socket.IO {@code socket.handshake} object.
 * All properties are immutable snapshots captured when the client connected.
 *
 * <pre>{@code
 * io.on("connection", socket -> {
 *     Handshake hs = socket.handshake();
 *
 *     // Auth data sent from the client: io({auth: {token: "abc"}})
 *     String token = (String) hs.auth().get("token");
 *
 *     // HTTP headers from the upgrade request
 *     String ua = hs.headers().get("user-agent");
 *
 *     // URL query parameters: /socket.io/?token=abc
 *     String queryToken = hs.query().get("token");
 *
 *     // Client IP address
 *     String ip = hs.address();
 * });
 * }</pre>
 */
public final class Handshake {

    /** Lower-cased request headers from the upgrade HTTP request. */
    public final Map<String, String> headers;

    /** Query parameters from the upgrade URL (e.g. {@code ?EIO=4&transport=websocket&token=x}). */
    public final Map<String, String> query;

    /**
     * Auth payload sent in the Socket.IO CONNECT packet data
     * (e.g. {@code { "token": "..." }}). Never {@code null} — empty map when absent.
     */
    public final Map<String, Object> auth;

    /** Client IP address. */
    public final String address;

    /** The URL path of the upgrade request (e.g. {@code /socket.io/}). */
    public final String url;

    /** Time when the connection was established. */
    public final Instant issued;

    /**
     * Create a handshake snapshot.
     *
     * @param headers  lower-cased HTTP request headers
     * @param query    URL query parameters
     * @param auth     auth data from the Socket.IO CONNECT packet; {@code null} treated as empty
     * @param address  client IP address
     * @param url      upgrade request URL path
     */
    public Handshake(Map<String, String> headers, Map<String, String> query,
                     Map<String, Object> auth, String address, String url) {
        this.headers = Map.copyOf(headers);
        this.query = Map.copyOf(query);
        this.auth = auth != null ? Map.copyOf(auth) : Map.of();
        this.address = address;
        this.url = url;
        this.issued = Instant.now();
    }

    // Accessor methods (IDE-friendly hover-tooltip API)

    /**
     * Lower-cased HTTP request headers from the WebSocket upgrade request.
     * Equivalent to {@code socket.handshake.headers} in Node.js Socket.IO.
     *
     * <pre>{@code
     * String ua = socket.handshake().headers().get("user-agent");
     * }</pre>
     *
     * @return immutable map of header name → value
     */
    public Map<String, String> headers() { return headers; }

    /**
     * URL query parameters from the upgrade request.
     * Equivalent to {@code socket.handshake.query} in Node.js Socket.IO.
     *
     * <p>For a connection URL like {@code /socket.io/?token=abc}, this map contains
     * {@code {"token": "abc", "EIO": "4", "transport": "websocket"}}.
     *
     * @return immutable map of parameter name → value
     */
    public Map<String, String> query() { return query; }

    /**
     * Auth data sent by the client in the Socket.IO CONNECT packet.
     * Equivalent to {@code socket.handshake.auth} in Node.js Socket.IO.
     *
     * <p>Populated when the client passes an {@code auth} option:
     * <pre>{@code
     * // JavaScript client:
     * const socket = io({ auth: { token: "my-jwt" } });
     *
     * // Java server:
     * String token = (String) socket.handshake().auth().get("token");
     * }</pre>
     *
     * @return immutable map of auth key → value; never {@code null}, empty if the client sent none
     */
    public Map<String, Object> auth() { return auth; }

    /**
     * Client IP address from the upgrade request.
     * Equivalent to {@code socket.handshake.address} in Node.js Socket.IO.
     *
     * @return the remote IP address, e.g. {@code "127.0.0.1"}
     */
    public String address() { return address; }

    /**
     * URL path of the upgrade request (e.g. {@code /socket.io/}).
     * Equivalent to {@code socket.handshake.url} in Node.js Socket.IO.
     *
     * @return the raw request URL
     */
    public String url() { return url; }

    /**
     * Timestamp when this connection was established.
     * Equivalent to {@code socket.handshake.issued} in Node.js Socket.IO.
     *
     * @return the connection time as an {@link Instant}
     */
    public Instant issued() { return issued; }
}
