package io.github.dhruvrawatdev.expressify.websocket;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Undertow {@link HttpHandler} that intercepts WebSocket upgrade requests and dispatches them
 * to the appropriate {@link WsServer} based on the request path.
 *
 * <p>Sits at the top of the request-handling chain, ahead of the normal HTTP adapter.
 * Non-WebSocket requests and upgrade requests with no matching path are forwarded to {@code next}.
 *
 * <p>This class is framework-internal — applications interact through {@link WsServer}
 * and the {@code Expressify.ws()} / {@code Expressify.wsServer()} methods.
 */
public final class WsUpgradeHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(WsUpgradeHandler.class);

    private final WsRegistry registry;
    private final HttpHandler next;

    public WsUpgradeHandler(WsRegistry registry, HttpHandler next) {
        this.registry = registry;
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // Only intercept HTTP Upgrade: websocket requests
        String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
        if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
            String path = exchange.getRequestPath();
            WsServer server = registry.findMatch(path);
            if (server != null) {
                try {
                    server.handleUpgrade(exchange);
                } catch (Exception e) {
                    log.error("WebSocket upgrade failed for path {}", path, e);
                    if (!exchange.isResponseStarted()) {
                        exchange.setStatusCode(500);
                        exchange.endExchange();
                    }
                }
                return;
            }
        }
        next.handleRequest(exchange);
    }
}
