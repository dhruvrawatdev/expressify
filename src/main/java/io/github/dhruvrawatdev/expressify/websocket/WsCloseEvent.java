package io.github.dhruvrawatdev.expressify.websocket;

/**
 * WebSocket close event — carries the close code and reason string.
 *
 * <p>Mirrors the parameters in Node.js ws:
 * {@code ws.on('close', (code, reason) => { })}.
 *
 * <h3>Common close codes</h3>
 * <table border="1">
 *   <tr><th>Code</th><th>Meaning</th></tr>
 *   <tr><td>1000</td><td>Normal Closure</td></tr>
 *   <tr><td>1001</td><td>Going Away</td></tr>
 *   <tr><td>1002</td><td>Protocol Error</td></tr>
 *   <tr><td>1003</td><td>Unsupported Data</td></tr>
 *   <tr><td>1006</td><td>Abnormal Closure (no close frame received)</td></tr>
 *   <tr><td>1007</td><td>Invalid frame payload data</td></tr>
 *   <tr><td>1008</td><td>Policy Violation</td></tr>
 *   <tr><td>1009</td><td>Message Too Big</td></tr>
 *   <tr><td>1011</td><td>Internal Error</td></tr>
 * </table>
 */
public final class WsCloseEvent {

    private final int code;
    private final String reason;

    WsCloseEvent(int code, String reason) {
        this.code = code;
        this.reason = reason != null ? reason : "";
    }

    /** WebSocket close status code (RFC 6455 §7.4). */
    public int code() { return code; }

    /** Human-readable reason string; may be empty. */
    public String reason() { return reason; }

    /**
     * {@code true} if the connection closed cleanly — i.e. a close frame was
     * exchanged and the code is in the range 1000–2999.
     */
    public boolean wasClean() { return code >= 1000 && code < 3000; }

    @Override
    public String toString() {
        return "WsCloseEvent{code=" + code + ", reason='" + reason + "'}";
    }
}
