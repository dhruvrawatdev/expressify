package io.github.dhruvrawatdev.expressify.socket.internal;

import io.github.dhruvrawatdev.expressify.socket.Handshake;
import io.github.dhruvrawatdev.expressify.socket.IoServerOptions;
import io.github.dhruvrawatdev.expressify.socket.IoSocket;
import io.github.dhruvrawatdev.expressify.socket.Namespace;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.WebSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Undertow {@link HttpHandler} that handles Socket.IO upgrade requests
 * ({@code GET /socket.io/?EIO=4&transport=websocket}).
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Match the request path against the configured Socket.IO endpoint</li>
 *   <li>Add CORS headers and handle OPTIONS preflight for browser clients</li>
 *   <li>Reject non-WebSocket requests with a proper Engine.IO JSON error</li>
 *   <li>Enforce EIO=4 (Engine.IO v4 protocol)</li>
 *   <li>Perform the WebSocket handshake via Undertow</li>
 *   <li>Create an {@link EngineSocket}, send the EIO OPEN packet, start heartbeat
 *       and connect-timeout timer</li>
 *   <li>Wait for Socket.IO CONNECT packet(s) — Socket.IO v4 allows one transport to
 *       multiplex multiple namespaces (e.g. {@code /} and {@code /admin})</li>
 *   <li>Dispatch all subsequent packets to the owning {@link IoSocket} by namespace</li>
 *   <li>On transport close, disconnect all active namespace sockets</li>
 * </ol>
 *
 * <p>Compatible with:
 * <ul>
 *   <li>socket.io-client npm 4.x (latest 4.8.x)</li>
 *   <li>socket_io_client Flutter/Dart 3.x (latest 3.1.x)</li>
 *   <li>Any Engine.IO v4 / Socket.IO v5 protocol client</li>
 * </ul>
 */
public final class IoUpgradeHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(IoUpgradeHandler.class);

    // Pre-allocated header name constants to avoid repeated allocations
    private static final HttpString HDR_ALLOW_ORIGIN = new HttpString("Access-Control-Allow-Origin");
    private static final HttpString HDR_ALLOW_CREDENTIALS = new HttpString("Access-Control-Allow-Credentials");
    private static final HttpString HDR_ALLOW_HEADERS = new HttpString("Access-Control-Allow-Headers");
    private static final HttpString HDR_ALLOW_METHODS = new HttpString("Access-Control-Allow-Methods");
    private static final HttpString HDR_CONTENT_TYPE = new HttpString("Content-Type");

    private final IoServerOptions options;
    private final Function<String, Namespace> namespaceResolver;
    private final HttpHandler next;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong sidCounter = new AtomicLong(0);

    public IoUpgradeHandler(IoServerOptions options, Function<String, Namespace> namespaceResolver, HttpHandler next) {
        this.options = options;
        this.namespaceResolver = namespaceResolver;
        this.next = next;
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "expressifyio-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    // HttpHandler

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRequestPath();

        // Only intercept requests under the Socket.IO path prefix
        boolean isSocketIoPath = path.equals(options.path) || path.startsWith(options.path + "/");
        if (!isSocketIoPath) {
            next.handleRequest(exchange);
            return;
        }

        // Always add CORS headers for any Socket.IO path response (needed for browser error
        // responses to be readable by the client, and for the WebSocket upgrade itself)
        addCorsHeaders(exchange);

        // Handle CORS OPTIONS preflight — browsers send this before the actual request
        String method = exchange.getRequestMethod().toString();
        if ("OPTIONS".equals(method)) {
            exchange.setStatusCode(204);
            exchange.endExchange();
            return;
        }

        String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
        boolean isWsUpgrade = "websocket".equalsIgnoreCase(upgrade);

        // Non-WebSocket request on the Socket.IO path — reject with a proper JSON error.
        // socket.io-client tries polling first unless transports:['websocket'] is set.
        if (!isWsUpgrade) {
            String transport = queryParam(exchange, "transport");
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(HDR_CONTENT_TYPE, "application/json; charset=utf-8");
            if ("polling".equals(transport)) {
                exchange.getResponseSender().send(
                    "{\"code\":1,\"message\":\"Only WebSocket transport is supported. " +
                    "Set transports: ['websocket'] in your Socket.IO client options.\"}");
            } else {
                exchange.getResponseSender().send(
                    "{\"code\":0,\"message\":\"Transport unknown\"}");
            }
            return;
        }

        // Enforce Engine.IO v4 — socket.io-client 4.x and socket_io_client 3.x both use EIO=4
        String eio = queryParam(exchange, "EIO");
        if (!"4".equals(eio)) {
            exchange.setStatusCode(400);
            exchange.getResponseHeaders().put(HDR_CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(
                "{\"code\":5,\"message\":\"Unsupported Engine.IO protocol version. Requires EIO=4.\"}");
            return;
        }

        // Capture all handshake data from the HTTP upgrade request
        Map<String, String> headers = extractHeaders(exchange);
        Map<String, String> query   = extractQuery(exchange);
        String address = exchange.getSourceAddress() != null
                ? exchange.getSourceAddress().getAddress().getHostAddress() : "unknown";
        String url = exchange.getRequestURI();

        // Perform the WebSocket handshake via Undertow
        WebSocketConnectionCallback callback = (wsExchange, channel) ->
                onConnect(channel, headers, query, address, url);
        new WebSocketProtocolHandshakeHandler(callback).handleRequest(exchange);
    }

    // Transport connected

    private void onConnect(WebSocketChannel channel, Map<String, String> headers, Map<String, String> query, String address, String url) {
        String sid = generateSid();
        EngineSocket engine = new EngineSocket(channel, sid, options.pingInterval, options.pingTimeout);

        // Socket.IO v4 multiplexes multiple namespace connections over a single transport.
        // Key = namespace path (e.g. "/" or "/admin"), value = IoSocket for that namespace.
        final Map<String, IoSocket> socketsByNs = new ConcurrentHashMap<>();

        // connectTimeout: close the transport if no CONNECT packet arrives within the window.
        // This prevents zombie connections that complete the WebSocket handshake but never
        // send a Socket.IO CONNECT (e.g. scripts hammering the endpoint).
        final ScheduledFuture<?>[] connectTimer = { null };
        connectTimer[0] = scheduler.schedule(() -> {
            if (socketsByNs.isEmpty() && !engine.isClosed()) {
                log.debug("Connect timeout ({}ms) — no SIO CONNECT from sid {}", options.connectTimeout, sid);
                engine.close();
            }
        }, options.connectTimeout, TimeUnit.MILLISECONDS);

        engine.onMessage(payload -> {
            // Cancel the connect timer on the first Socket.IO packet received
            cancelTimer(connectTimer);
            handleSioPacket(payload, engine, headers, query, address, url, socketsByNs);
        });

        engine.onClose(() -> {
            cancelTimer(connectTimer);
            // Disconnect every namespace socket multiplexed on this transport
            for (IoSocket sock : socketsByNs.values()) {
                try { sock.onDisconnect("transport close"); } catch (Exception ignored) {}
            }
            socketsByNs.clear();
        });

        // Send EIO OPEN packet and start heartbeat scheduler
        engine.open(scheduler, options.maxPayload);
    }

    private static void cancelTimer(ScheduledFuture<?>[] slot) {
        if (slot[0] != null) { slot[0].cancel(false); slot[0] = null; }
    }

    // SIO packet dispatch

    private void handleSioPacket(String payload, EngineSocket engine,
                                 Map<String, String> headers,
                                 Map<String, String> query, String address,
                                 String url, Map<String, IoSocket> socketsByNs) {
        Packet packet;
        try {
            packet = PacketCodec.decode(payload);
        } catch (Exception e) {
            log.warn("Invalid SIO packet from sid {}: {}", engine.sid(), payload, e);
            return;
        }

        switch (packet.type) {
            case CONNECT ->
                handleConnect(packet, engine, headers, query, address, url, socketsByNs);

            case DISCONNECT -> {
                // Client is leaving one specific namespace (not closing the transport)
                IoSocket sock = socketsByNs.remove(packet.namespace);
                if (sock != null) sock.onDisconnect("client namespace disconnect");
            }

            case EVENT, ACK -> {
                IoSocket sock = socketsByNs.get(packet.namespace);
                if (sock != null) {
                    sock.dispatch(packet);
                } else {
                    log.debug("Received {} for unconnected ns '{}' on sid {}",
                            packet.type, packet.namespace, engine.sid());
                }
            }

            case BINARY_EVENT, BINARY_ACK -> {
                // Binary attachments are not supported — send a graceful CONNECT_ERROR so the
                // client knows rather than hanging silently
                log.debug("Binary packet received on sid {} (ns '{}') — not supported",
                        engine.sid(), packet.namespace);
                engine.sendMessage(PacketCodec.encodeConnectError(
                        packet.namespace, "Binary events are not supported by this server"));
            }

            default -> log.debug("Ignoring unknown packet type {} from sid {}", packet.type, engine.sid());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConnect(Packet packet, EngineSocket engine,
                               Map<String, String> headers, Map<String, String> query,
                               String address, String url,
                               Map<String, IoSocket> socketsByNs) {
        String nsName = packet.namespace;

        // Reject duplicate CONNECT for the same namespace on this transport
        if (socketsByNs.containsKey(nsName)) {
            log.debug("Duplicate CONNECT for ns '{}' on sid {} — ignored", nsName, engine.sid());
            return;
        }

        Namespace ns = namespaceResolver.apply(nsName);
        if (ns == null) {
            // Unknown namespace — notify client so it can emit "connect_error"
            engine.sendMessage(PacketCodec.encodeConnectError(nsName, "Invalid namespace"));
            return;
        }

        // Extract auth data sent in the Socket.IO CONNECT packet (e.g. {"token":"..."})
        Map<String, Object> auth = Map.of();
        if (!packet.data.isEmpty() && packet.data.get(0) instanceof Map) {
            auth = (Map<String, Object>) packet.data.get(0);
        }

        Handshake hs = new Handshake(headers, query, auth, address, url);
        IoSocket socket = new IoSocket(engine.sid(), ns, engine, ns.adapter(), hs);
        socketsByNs.put(nsName, socket);

        // addSocket runs middleware chain then sends CONNECT ack + fires connection event
        ns.addSocket(socket);
    }

    // CORS

    private static void addCorsHeaders(HttpServerExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            // Reflect the request Origin — allows any origin.
            // For production deployments, validate origin against an allow-list before reflecting.
            exchange.getResponseHeaders().put(HDR_ALLOW_ORIGIN,      origin);
            exchange.getResponseHeaders().put(HDR_ALLOW_CREDENTIALS, "true");
            exchange.getResponseHeaders().put(HDR_ALLOW_HEADERS,
                    "Origin, X-Requested-With, Content-Type, Accept, Authorization");
            exchange.getResponseHeaders().put(HDR_ALLOW_METHODS, "GET, POST, OPTIONS");
        }
    }

    // Helpers

    private String generateSid() {
        return Long.toString(sidCounter.incrementAndGet(), 36)
                + Long.toString(System.nanoTime(), 36);
    }

    private static String queryParam(HttpServerExchange exchange, String name) {
        Deque<String> vals = exchange.getQueryParameters().get(name);
        return (vals != null && !vals.isEmpty()) ? vals.peekFirst() : null;
    }

    private static Map<String, String> extractHeaders(HttpServerExchange exchange) {
        Map<String, String> map = new HashMap<>();
        exchange.getRequestHeaders().forEach(header -> {
            String name  = header.getHeaderName().toString().toLowerCase(Locale.ROOT);
            String value = header.getFirst();
            if (value != null) map.put(name, value);
        });
        return map;
    }

    private static Map<String, String> extractQuery(HttpServerExchange exchange) {
        Map<String, String> map = new HashMap<>();
        exchange.getQueryParameters().forEach((k, v) -> {
            if (!v.isEmpty()) map.put(k, v.peekFirst());
        });
        return map;
    }

    /** Shut down the heartbeat and connect-timeout scheduler. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
