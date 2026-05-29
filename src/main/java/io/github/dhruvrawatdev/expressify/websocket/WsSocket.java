package io.github.dhruvrawatdev.expressify.websocket;

import io.undertow.websockets.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Per-connection WebSocket handle — wraps an Undertow {@link WebSocketChannel}.
 *
 * <p>Mirrors the Node.js {@code ws.WebSocket} client-side API that is passed to
 * the {@code connection} event handler. Attach listeners here; call {@link #send}
 * to push messages to the client.
 *
 * <h3>Events (Node.js → Java equivalent)</h3>
 * <table border="1">
 *   <tr><th>Node.js</th><th>Expressify</th></tr>
 *   <tr><td>{@code ws.on('message', fn)}</td><td>{@link #onMessage}</td></tr>
 *   <tr><td>{@code ws.on('close',   fn)}</td><td>{@link #onClose}</td></tr>
 *   <tr><td>{@code ws.on('error',   fn)}</td><td>{@link #onError}</td></tr>
 *   <tr><td>{@code ws.on('ping',    fn)}</td><td>{@link #onPing}</td></tr>
 *   <tr><td>{@code ws.on('pong',    fn)}</td><td>{@link #onPong}</td></tr>
 *   <tr><td>{@code ws.on('open',    fn)}</td><td>{@link #onOpen}</td></tr>
 * </table>
 *
 * <h3>Quick example</h3>
 * <pre>{@code
 * app.ws("/echo", (ws, req) -> {
 *     ws.onMessage(msg -> ws.send("Echo: " + msg.asText()));
 *     ws.onClose(ev  -> System.out.println("Disconnected: " + ev.code()));
 *     ws.onError(err -> err.printStackTrace());
 * });
 * }</pre>
 */
public final class WsSocket {

    private static final Logger log = LoggerFactory.getLogger(WsSocket.class);

    // ── ReadyState enum ────────────────────────────────────────────────────

    /**
     * Connection lifecycle state — mirrors the {@code readyState} constants in
     * the browser WebSocket API and Node.js ws.
     */
    public enum ReadyState {
        /** Handshake in progress (internal only — not observable by connection handlers). */
        CONNECTING,
        /** Connection is open and messages can be sent/received. */
        OPEN,
        /** Close handshake in progress; no new messages may be sent. */
        CLOSING,
        /** Connection fully closed. */
        CLOSED
    }

    // ── Fields ─────────────────────────────────────────────────────────────

    private final WebSocketChannel channel;
    private final WsServerOptions options;
    private volatile ReadyState readyState = ReadyState.CONNECTING;
    private volatile boolean alive = true;

    private final Map<String, Object>       locals  = new ConcurrentHashMap<>();

    // Event listener lists — CopyOnWriteArrayList = safe for concurrent add+iterate
    private final List<Consumer<WsMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<WsCloseEvent>> closeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<byte[]>> pingListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<byte[]>> pongListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> openListeners = new CopyOnWriteArrayList<>();

    WsSocket(WebSocketChannel channel, WsServerOptions options) {
        this.channel = channel;
        this.options = options;
        // Register a channel close task so CLOSED fires even on abnormal disconnects
        channel.addCloseTask(ch -> fireClose(1006, "Abnormal closure"));
    }

    // Event registration

    /**
     * Register a listener for incoming text or binary messages.
     * Equivalent to {@code ws.on('message', (data, isBinary) => { })} in Node.js ws.
     *
     * <pre>{@code
     * ws.onMessage(msg -> {
     *     if (msg.isText()) ws.send("Echo: " + msg.asText());
     * });
     * }</pre>
     */
    public WsSocket onMessage(Consumer<WsMessage> listener) {
        messageListeners.add(listener);
        return this;
    }

    /**
     * Register a listener for the connection-close event.
     * Equivalent to {@code ws.on('close', (code, reason) => { })} in Node.js ws.
     *
     * <pre>{@code
     * ws.onClose(ev -> System.out.println("Closed: " + ev.code() + " " + ev.reason()));
     * }</pre>
     */
    public WsSocket onClose(Consumer<WsCloseEvent> listener) {
        closeListeners.add(listener);
        return this;
    }

    /**
     * Register a listener for error events.
     * Equivalent to {@code ws.on('error', err => { })} in Node.js ws.
     *
     * <p>Registering an error listener suppresses the default error log.
     *
     * <pre>{@code
     * ws.onError(err -> log.error("WebSocket error", err));
     * }</pre>
     */
    public WsSocket onError(Consumer<Throwable> listener) {
        errorListeners.add(listener);
        return this;
    }

    /**
     * Register a listener for incoming ping frames.
     * Equivalent to {@code ws.on('ping', data => { })} in Node.js ws.
     *
     * <p>Note: {@link WsServerOptions#autoPong} (default {@code true}) sends
     * pong automatically — you do not need to call {@link #pong} manually.
     */
    public WsSocket onPing(Consumer<byte[]> listener) {
        pingListeners.add(listener);
        return this;
    }

    /**
     * Register a listener for incoming pong frames.
     * Equivalent to {@code ws.on('pong', data => { })} in Node.js ws.
     *
     * <p>Commonly used to implement heartbeat logic:
     * <pre>{@code
     * ws.setAlive(false);
     * ws.ping();
     * ws.onPong(data -> ws.setAlive(true));
     * }</pre>
     */
    public WsSocket onPong(Consumer<byte[]> listener) {
        pongListeners.add(listener);
        return this;
    }

    /**
     * Register a listener invoked immediately after the connection is established.
     * Equivalent to {@code ws.on('open', () => { })} in Node.js ws.
     */
    public WsSocket onOpen(Runnable listener) {
        openListeners.add(listener);
        return this;
    }

    // Sending

    /**
     * Send a text message to the client.
     * Equivalent to {@code ws.send(data)} in Node.js ws.
     *
     * <p>No-op if the connection is not {@link ReadyState#OPEN OPEN}.
     */
    public void send(String data) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendText(data, channel, null);
    }

    /**
     * Send a binary message to the client.
     * Equivalent to {@code ws.send(buffer)} in Node.js ws.
     *
     * <p>No-op if the connection is not {@link ReadyState#OPEN OPEN}.
     */
    public void send(byte[] data) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendBinary(ByteBuffer.wrap(data), channel, null);
    }

    /**
     * Send a text message with a completion callback.
     *
     * <pre>{@code
     * ws.send("Hello", new WebSocketCallback<Void>() {
     *     public void complete(WebSocketChannel channel, Void context) { }
     *     public void onError(WebSocketChannel channel, Void context, Throwable throwable) {
     *         log.error("Send failed", throwable);
     *     }
     * });
     * }</pre>
     */
    public void send(String data, WebSocketCallback<Void> callback) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendText(data, channel, callback);
    }

    /**
     * Send a binary message with a completion callback.
     */
    public void send(byte[] data, WebSocketCallback<Void> callback) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendBinary(ByteBuffer.wrap(data), channel, callback);
    }

    // Ping / Pong

    /**
     * Send a ping frame with no payload.
     * Equivalent to {@code ws.ping()} in Node.js ws.
     *
     * <p>The remote peer should respond with a pong — use {@link #onPong} to detect it.
     */
    public void ping() { ping(new byte[0]); }

    /**
     * Send a ping frame with a data payload.
     * Equivalent to {@code ws.ping(data)} in Node.js ws.
     */
    public void ping(byte[] data) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendPing(ByteBuffer.wrap(data), channel, null);
    }

    /**
     * Send a pong frame with no payload.
     * Equivalent to {@code ws.pong()} in Node.js ws.
     */
    public void pong() { pong(new byte[0]); }

    /**
     * Send a pong frame with a data payload.
     * Equivalent to {@code ws.pong(data)} in Node.js ws.
     */
    public void pong(byte[] data) {
        if (readyState != ReadyState.OPEN) return;
        WebSockets.sendPong(ByteBuffer.wrap(data), channel, null);
    }

    // Closing

    /**
     * Initiate a graceful close with code {@code 1000} (Normal Closure).
     * Equivalent to {@code ws.close()} in Node.js ws.
     */
    public void close() { close(1000, ""); }

    /**
     * Initiate a graceful close with the given status code.
     * Equivalent to {@code ws.close(code)} in Node.js ws.
     */
    public void close(int code) { close(code, ""); }

    /**
     * Initiate a graceful close with a status code and human-readable reason.
     * Equivalent to {@code ws.close(code, reason)} in Node.js ws.
     *
     * <p>Sends a WebSocket close frame; the connection is fully closed once
     * the remote peer acknowledges with its own close frame.
     */
    public void close(int code, String reason) {
        if (readyState == ReadyState.CLOSED || readyState == ReadyState.CLOSING) return;
        readyState = ReadyState.CLOSING;
        WebSockets.sendClose(new CloseMessage(code, reason != null ? reason : ""), channel, null);
    }

    /**
     * Force-terminate the connection immediately without a close handshake.
     * Equivalent to {@code ws.terminate()} in Node.js ws.
     *
     * <p>Use when the graceful close handshake is unresponsive or unnecessary.
     */
    public void terminate() {
        if (readyState == ReadyState.CLOSED) return;
        readyState = ReadyState.CLOSING;
        try {
            channel.close();
        } catch (IOException e) {
            log.debug("Error terminating WebSocket channel", e);
        }
    }

    // Pause / Resume (mirrors Node.js ws)

    /**
     * Pause receiving messages from the client.
     * Equivalent to {@code ws.pause()} in Node.js ws.
     * Useful for backpressure — call {@link #resume()} to restart.
     */
    public void pause() {
        channel.suspendReceives();
    }

    /**
     * Resume receiving messages after a {@link #pause()}.
     * Equivalent to {@code ws.resume()} in Node.js ws.
     */
    public void resume() {
        channel.resumeReceives();
    }

    // Properties

    /**
     * Current connection state.
     * Mirrors the {@code readyState} property in Node.js ws / browser WebSocket API.
     */
    public ReadyState readyState() { return readyState; }

    /**
     * Shorthand for {@code readyState() == ReadyState.OPEN}.
     */
    public boolean isOpen() { return readyState == ReadyState.OPEN; }

    /**
     * Negotiated WebSocket subprotocol, or an empty string if none was negotiated.
     * Mirrors the {@code protocol} property in Node.js ws.
     */
    public String protocol() {
        String sub = channel.getSubProtocol();
        return sub != null ? sub : "";
    }

    /**
     * The remote address of the connected client.
     * Returns {@code "unknown"} if the address cannot be determined.
     */
    public String remoteAddress() {
        try {
            return channel.getPeerAddress() != null
                ? channel.getPeerAddress().toString()
                : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Per-connection data store — attach any application-level state here.
     * Equivalent to {@code ws.data} in Bun, or a custom property in Node.js ws.
     *
     * <pre>{@code
     * ws.locals().put("userId", req.query("userId"));
     * }</pre>
     */
    public Map<String, Object> locals() { return locals; }

    /**
     * Heartbeat alive flag — used to implement ping/pong heartbeat logic.
     * Set to {@code false} before sending a ping; set back to {@code true} in the pong listener.
     *
     * <pre>{@code
     * // Server-side heartbeat (run every 30 s)
     * wss.clients().forEach(ws -> {
     *     if (!ws.isAlive()) { ws.terminate(); return; }
     *     ws.setAlive(false);
     *     ws.ping();
     * });
     * // In the connection handler:
     * ws.onPong(data -> ws.setAlive(true));
     * }</pre>
     */
    public boolean isAlive() { return alive; }

    /** Set the heartbeat alive flag. */
    public void setAlive(boolean alive) { this.alive = alive; }

    /**
     * The underlying Undertow {@link WebSocketChannel} — for advanced use.
     * Prefer the public API methods for all standard operations.
     */
    public WebSocketChannel channel() { return channel; }

    // Internal — called by WsServer

    void fireOpen() {
        readyState = ReadyState.OPEN;
        for (Runnable l : openListeners) {
            try { l.run(); } catch (Exception e) { fireError(e); }
        }
    }

    void fireMessage(WsMessage msg) {
        for (Consumer<WsMessage> l : messageListeners) {
            try { l.accept(msg); } catch (Exception e) { fireError(e); }
        }
    }

    void firePing(byte[] data) {
        if (options.autoPong) pong(data);
        for (Consumer<byte[]> l : pingListeners) {
            try { l.accept(data); } catch (Exception e) { fireError(e); }
        }
    }

    void firePong(byte[] data) {
        alive = true; // mark alive on any pong for heartbeat logic
        for (Consumer<byte[]> l : pongListeners) {
            try { l.accept(data); } catch (Exception e) { fireError(e); }
        }
    }

    void fireClose(int code, String reason) {
        if (readyState == ReadyState.CLOSED) return; // idempotent
        readyState = ReadyState.CLOSED;
        WsCloseEvent ev = new WsCloseEvent(code, reason);
        for (Consumer<WsCloseEvent> l : closeListeners) {
            try { l.accept(ev); } catch (Exception e) {
                log.debug("Exception in WsSocket close listener", e);
            }
        }
    }

    void fireError(Throwable err) {
        if (errorListeners.isEmpty()) {
            log.debug("Unhandled WebSocket error on {}", remoteAddress(), err);
        } else {
            for (Consumer<Throwable> l : errorListeners) {
                try { l.accept(err); } catch (Exception e) {
                    log.debug("Exception in WsSocket error listener", e);
                }
            }
        }
    }

    /** Build the Undertow receive listener that dispatches frames to this socket's event listeners. */
    @SuppressWarnings("deprecation") // Undertow's Pooled<ByteBuffer[]> API — no replacement while on Undertow 2.x
    AbstractReceiveListener buildReceiveListener() {
        return new AbstractReceiveListener() {

            // Enforce maxPayload — Undertow closes the channel with code 1009 (Message Too Big)
            // when a buffered message exceeds these limits, which then fires onError/onClose.
            @Override
            protected long getMaxTextBufferSize()   { return options.maxPayload; }
            @Override
            protected long getMaxBinaryBufferSize() { return options.maxPayload; }

            @Override
            protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage message) {
                fireMessage(WsMessage.ofText(message.getData()));
            }

            @Override
            protected void onFullBinaryMessage(WebSocketChannel ch, BufferedBinaryMessage message) {
                try (Pooled<ByteBuffer[]> pooled = message.getData()) {
                    fireMessage(WsMessage.ofBinary(collectBytes(pooled.getResource())));
                }
            }

            @Override
            protected void onFullPingMessage(WebSocketChannel ch, BufferedBinaryMessage message) {
                try (Pooled<ByteBuffer[]> pooled = message.getData()) {
                    firePing(collectBytes(pooled.getResource()));
                }
            }

            @Override
            protected void onFullPongMessage(WebSocketChannel ch, BufferedBinaryMessage message) {
                try (Pooled<ByteBuffer[]> pooled = message.getData()) {
                    firePong(collectBytes(pooled.getResource()));
                }
            }

            @Override
            protected void onCloseMessage(CloseMessage cm, WebSocketChannel ch) {
                // Acknowledge with a close frame if the remote peer initiated the close
                if (readyState == ReadyState.OPEN) {
                    WebSockets.sendClose(new CloseMessage(cm.getCode(), cm.getReason()), ch, null);
                }
                fireClose(cm.getCode(), cm.getReason());
            }

            @Override
            protected void onError(WebSocketChannel ch, Throwable error) {
                fireError(error);
            }

            private byte[] collectBytes(ByteBuffer[] buffers) {
                int total = 0;
                for (ByteBuffer b : buffers) total += b.remaining();
                byte[] out = new byte[total];
                int pos = 0;
                for (ByteBuffer b : buffers) {
                    int len = b.remaining();
                    b.get(out, pos, len);
                    pos += len;
                }
                return out;
            }
        };
    }
}
