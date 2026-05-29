package io.github.dhruvrawatdev.expressify.websocket;

import io.github.dhruvrawatdev.expressify.http.Request;

/**
 * Information about an incoming WebSocket connection, passed to
 * {@link WsServerOptions.VerifyClient} so the application can accept or reject it.
 *
 * <p>Mirrors the {@code info} object in Node.js ws {@code verifyClient} callback:
 * {@code { origin, req, secure }}.
 *
 * <pre>{@code
 * WsServerOptions opts = WsServerOptions.builder()
 *     .verifyClient(info -> {
 *         String token = info.req().get("Authorization");
 *         return token != null && isValid(token);
 *     })
 *     .build();
 * app.ws("/secure", opts, (ws, req) -> { ... });
 * }</pre>
 */
public final class WsClientInfo {

    private final String  origin;
    private final Request req;

    WsClientInfo(String origin, Request req) {
        this.origin = origin;
        this.req    = req;
    }

    /**
     * Value of the HTTP {@code Origin} header from the upgrade request,
     * or {@code null} if the header was not present.
     */
    public String origin() { return origin; }

    /**
     * The HTTP upgrade request — use it to read headers, query params,
     * path params, and {@code req.locals()} for custom auth data.
     */
    public Request req() { return req; }
}
