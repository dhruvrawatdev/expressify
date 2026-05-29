package io.github.dhruvrawatdev.expressify.websocket;

import io.github.dhruvrawatdev.expressify.http.Request;

/**
 * Callback invoked when a WebSocket client connects.
 *
 * <p>Equivalent to the Node.js ws {@code connection} event:
 * {@code wss.on('connection', (ws, req) => { ... })}.
 *
 * <p>Register event listeners on {@code ws} inside this callback:
 * <pre>{@code
 * app.ws("/chat", (ws, req) -> {
 *     String username = req.query("name");
 *
 *     ws.onMessage(msg -> {
 *         ws.send("Echo from " + username + ": " + msg.asText());
 *     });
 *
 *     ws.onClose(ev -> {
 *         System.out.println(username + " disconnected — code: " + ev.code());
 *     });
 *
 *     ws.onError(err -> err.printStackTrace());
 *
 *     ws.send("Welcome, " + username + "!");
 * });
 * }</pre>
 */
@FunctionalInterface
public interface WsConnectionHandler {
    /**
     * Called once per accepted WebSocket connection.
     *
     * @param ws  the per-connection WebSocket handle — attach listeners and send messages here
     * @param req the HTTP upgrade request — access headers, path params, query params, etc.
     */
    void onConnect(WsSocket ws, Request req);
}
