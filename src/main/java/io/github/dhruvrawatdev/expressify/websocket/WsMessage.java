package io.github.dhruvrawatdev.expressify.websocket;

import java.nio.charset.StandardCharsets;

/**
 * Incoming WebSocket message — wraps a text or binary payload.
 *
 * <p>Mirrors the {@code data} and {@code isBinary} parameters in Node.js ws:
 * {@code ws.on('message', (data, isBinary) => { })}.
 *
 * <pre>{@code
 * ws.onMessage(msg -> {
 *     if (msg.isText()) {
 *         String text = msg.asText();
 *         ws.send("Echo: " + text);
 *     } else {
 *         byte[] bytes = msg.asBytes();
 *         ws.send(bytes);
 *     }
 * });
 * }</pre>
 */
public final class WsMessage {

    private final String text;
    private final byte[] bytes;
    private final boolean binary;

    private WsMessage(String text, byte[] bytes, boolean binary) {
        this.text = text;
        this.bytes = bytes;
        this.binary = binary;
    }

    static WsMessage ofText(String data) {
        return new WsMessage(data, null, false);
    }

    static WsMessage ofBinary(byte[] data) {
        return new WsMessage(null, data, true);
    }

    /** {@code true} if this is a text frame. */
    public boolean isText() { return !binary; }

    /** {@code true} if this is a binary frame. */
    public boolean isBinary() { return binary; }

    /**
     * Return message as a UTF-8 string.
     * Binary frames are decoded as UTF-8.
     */
    public String asText() {
        if (!binary) return text != null ? text : "";
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : "";
    }

    /**
     * Return message as a byte array.
     * Text frames are encoded as UTF-8.
     */
    public byte[] asBytes() {
        if (binary) return bytes != null ? bytes : new byte[0];
        return text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    /**
     * Raw data object: {@code String} for text frames, {@code byte[]} for binary frames.
     * Equivalent to the {@code data} argument in Node.js ws message callbacks.
     */
    public Object data() { return binary ? bytes : text; }

    @Override
    public String toString() {
        return binary
            ? "[binary " + (bytes != null ? bytes.length : 0) + " bytes]"
            : asText();
    }
}
