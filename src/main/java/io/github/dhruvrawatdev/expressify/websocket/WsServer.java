package io.github.dhruvrawatdev.expressify.websocket;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.router.internal.PathPattern;
import io.undertow.server.HttpServerExchange;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.protocol.version07.Hybi07Handshake;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import io.undertow.websockets.core.WebSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * WebSocket server endpoint — manages all connections at a specific path.
 *
 * <p>Mirrors the Node.js {@code ws.WebSocketServer} class.
 * Obtain an instance via {@code Expressify.wsServer()} or the value returned by {@code app.ws()}.
 *
 * <h3>Usage — broadcast to all clients</h3>
 * <pre>{@code
 * WsServer chat = app.wsServer("/chat");
 * chat.onConnection((ws, req) -> {
 *     String name = req.query("name");
 *
 *     ws.onMessage(msg ->
 *         chat.broadcast("[" + name + "]: " + msg.asText())
 *     );
 *
 *     ws.onClose(ev ->
 *         chat.broadcast(name + " left the room")
 *     );
 * });
 * }</pre>
 *
 * <h3>Usage — heartbeat (ping/pong)</h3>
 * <pre>{@code
 * WsServer wss = app.wsServer("/ws");
 * wss.onConnection((ws, req) -> ws.onPong(data -> ws.setAlive(true)));
 *
 * // Heartbeat interval — run this in a scheduled executor
 * wss.clients().forEach(ws -> {
 *     if (!ws.isAlive()) { ws.terminate(); return; }
 *     ws.setAlive(false);
 *     ws.ping();
 * });
 * }</pre>
 */
public final class WsServer {

    private static final Logger log = LoggerFactory.getLogger(WsServer.class);

    private final String path;
    private final PathPattern pathPattern;
    private final List<String> paramNames;
    private final WsServerOptions options;
    private final Set<WsSocket> clients;

    private final List<WsConnectionHandler> connectionHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> closeListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();

    /**
     * Create a WsServer for {@code path} with the given options.
     * Register it with the application via {@code app.ws(path, server)}.
     *
     * <pre>{@code
     * WsServer wss = new WsServer("/chat", WsServerOptions.builder()
     *     .clientTracking(true)
     *     .maxPayload(64 * 1024)
     *     .build());
     * app.ws(wss);   // or: app.ws("/chat", wss, (ws, req) -> { ... });
     * }</pre>
     */
    public WsServer(String path, WsServerOptions options) {
        this.path = path;
        this.options = options;
        this.clients = options.clientTracking ? new CopyOnWriteArraySet<>() : Collections.emptySet();

        List<String> names = new ArrayList<>();
        this.pathPattern = PathPattern.compile(path, names, false);
        this.paramNames = Collections.unmodifiableList(names);
    }

    /**
     * Create a WsServer for {@code path} with a pre-registered connection handler and custom options.
     * Equivalent to calling {@link #WsServer(String, WsServerOptions)} then {@link #onConnection(WsConnectionHandler)}.
     *
     * <pre>{@code
     * WsServer wss = new WsServer("/chat",
     *     (ws, req) -> ws.onMessage(msg -> ws.send("Echo: " + msg.asText())),
     *     WsServerOptions.builder().maxPayload(64 * 1024).build());
     * app.ws(wss);
     * }</pre>
     *
     * @param path    WebSocket path; supports {@code :param} placeholders and wildcards
     * @param handler the connection handler registered immediately
     * @param options server configuration options
     */
    public WsServer(String path, WsConnectionHandler handler, WsServerOptions options) {
        this(path, options);
        this.connectionHandlers.add(handler);
    }

    // Event registration (Node.js ws wss.on() equivalents)

    /**
     * Register a connection handler — called every time a client connects.
     * Equivalent to {@code wss.on('connection', (ws, req) => { })} in Node.js ws.
     *
     * <p>Multiple handlers can be registered; they are invoked in order.
     *
     * <pre>{@code
     * wss.onConnection((ws, req) -> {
     *     ws.onMessage(msg -> wss.broadcast(msg.asText()));
     * });
     * }</pre>
     */
    public WsServer onConnection(WsConnectionHandler handler) {
        connectionHandlers.add(handler);
        return this;
    }

    /**
     * Register a server-level error listener.
     * Equivalent to {@code wss.on('error', err => { })} in Node.js ws.
     */
    public WsServer onError(Consumer<Throwable> listener) {
        errorListeners.add(listener);
        return this;
    }

    /**
     * Register a callback invoked when the server closes (i.e. when the Expressify app stops).
     * Equivalent to {@code wss.on('close', () => { })} in Node.js ws.
     */
    public WsServer onClose(Runnable listener) {
        closeListeners.add(listener);
        return this;
    }

    // Broadcast

    /**
     * Send a text message to all currently connected clients.
     * Equivalent to iterating {@code wss.clients} and calling {@code ws.send(msg)} in Node.js ws.
     *
     * <pre>{@code
     * wss.broadcast("Server announcement: maintenance in 5 minutes");
     * }</pre>
     */
    public void broadcast(String message) {
        for (WsSocket ws : clients) {
            if (ws.isOpen()) ws.send(message);
        }
    }

    /**
     * Send a text message to all connected clients <em>except</em> {@code exclude}.
     * Useful for chat room fan-out where the sender should not receive their own message.
     *
     * <pre>{@code
     * ws.onMessage(msg ->
     *     wss.broadcast(msg.asText(), ws)  // send to everyone else
     * );
     * }</pre>
     */
    public void broadcast(String message, WsSocket exclude) {
        for (WsSocket ws : clients) {
            if (ws != exclude && ws.isOpen()) ws.send(message);
        }
    }

    /**
     * Send a binary message to all currently connected clients.
     */
    public void broadcast(byte[] data) {
        for (WsSocket ws : clients) {
            if (ws.isOpen()) ws.send(data);
        }
    }

    /**
     * Send a binary message to all connected clients except {@code exclude}.
     */
    public void broadcast(byte[] data, WsSocket exclude) {
        for (WsSocket ws : clients) {
            if (ws != exclude && ws.isOpen()) ws.send(data);
        }
    }

    // Client set

    /**
     * Return the live set of connected {@link WsSocket} instances.
     * Only populated when {@link WsServerOptions#clientTracking} is {@code true} (the default).
     *
     * <p>The returned set is a live view — it changes as clients connect and disconnect.
     * Mirrors {@code wss.clients} in Node.js ws.
     *
     * <pre>{@code
     * // Count active connections
     * int count = wss.clients().size();
     *
     * // Terminate all connections
     * wss.clients().forEach(WsSocket::terminate);
     * }</pre>
     */
    public Set<WsSocket> clients() { return Collections.unmodifiableSet(clients); }

    /** Number of currently connected clients. Equivalent to {@code wss.clients.size} in Node.js ws. */
    public int clientCount() { return clients.size(); }

    // Server state

    /** The path this server listens on (e.g. {@code "/chat"}). */
    public String path() { return path; }

    // Internal — called by WsUpgradeHandler

    /** Test whether {@code requestPath} matches this server's registered path. */
    boolean matches(String requestPath) {
        return pathPattern.matches(requestPath);
    }

    /**
     * Handle a WebSocket upgrade request.
     * Called from {@link WsUpgradeHandler} after path matching confirms this server owns the path.
     */
    void handleUpgrade(HttpServerExchange exchange) throws Exception {
        // Extract path parameters (e.g. /chat/:room → room=lounge)
        Map<String, String> params = new HashMap<>();
        if (!paramNames.isEmpty()) {
            pathPattern.match(exchange.getRequestPath(), params);
        }

        // Build a Request from the upgrade exchange so handlers can read headers, query, etc.
        Request req = new Request(exchange);
        if (!params.isEmpty()) req.setParams(params);

        // verifyClient
        if (options.verifyClient != null) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            WsClientInfo info = new WsClientInfo(origin, req);
            if (!options.verifyClient.verify(info)) {
                exchange.setStatusCode(401);
                exchange.getResponseSender().send("Unauthorized");
                return;
            }
        }

        // Protocol selection
        String selectedProtocol = null;
        if (options.handleProtocols != null) {
            String protocolHeader = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Protocol");
            if (protocolHeader != null) {
                List<String> offered = Arrays.asList(protocolHeader.split(",\\s*"));
                selectedProtocol = options.handleProtocols.select(offered, req);
            }
        }

        // WebSocket handshake
        final String finalProtocol = selectedProtocol;
        WebSocketConnectionCallback callback = (wsExchange, channel) ->
            onConnect(wsExchange, channel, req, finalProtocol);

        // Build handshake handler — include declared subprotocols if any
        WebSocketProtocolHandshakeHandler handler;
        if (!options.subprotocols.isEmpty()) {
            Set<String> protoSet = new HashSet<>(options.subprotocols);
            handler = new WebSocketProtocolHandshakeHandler(
                List.of(new Hybi13Handshake(protoSet, false),
                        new Hybi07Handshake(protoSet, false)),
                callback, null);
        } else {
            handler = new WebSocketProtocolHandshakeHandler(callback);
        }
        handler.handleRequest(exchange);
    }

    /** Called by Undertow after the WebSocket handshake succeeds. */
    private void onConnect(WebSocketHttpExchange wsExchange, WebSocketChannel channel,
                           Request req, String selectedProtocol) {
        WsSocket ws = new WsSocket(channel, options);

        if (options.clientTracking) {
            clients.add(ws);
            ws.onClose(ev -> clients.remove(ws));
        }

        // Invoke all registered connection handlers
        for (WsConnectionHandler handler : connectionHandlers) {
            try {
                handler.onConnect(ws, req);
            } catch (Exception e) {
                log.error("Exception in WsConnectionHandler for path {}", path, e);
                fireServerError(e);
            }
        }

        // Start receiving frames — this MUST happen after onConnect so listeners are registered
        ws.fireOpen();
        channel.getReceiveSetter().set(ws.buildReceiveListener());
        channel.resumeReceives();
    }

    /** Emit a server-level error (not a per-connection error). */
    private void fireServerError(Throwable err) {
        if (errorListeners.isEmpty()) {
            log.warn("Unhandled WsServer error on path {}", path, err);
        } else {
            for (Consumer<Throwable> l : errorListeners) {
                try { l.accept(err); } catch (Exception e) {
                    log.debug("Exception in WsServer error listener", e);
                }
            }
        }
    }

    /** Called by the Expressify application when it stops, to notify server close listeners. */
    public void notifyClose() {
        for (Runnable l : closeListeners) {
            try { l.run(); } catch (Exception e) {
                log.debug("Exception in WsServer close listener", e);
            }
        }
    }
}
