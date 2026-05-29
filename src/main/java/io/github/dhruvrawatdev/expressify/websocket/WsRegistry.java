package io.github.dhruvrawatdev.expressify.websocket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internal registry of {@link WsServer} instances, keyed by path.
 * Used by {@link WsUpgradeHandler} to route incoming upgrade requests.
 */
public final class WsRegistry {

    private final List<WsServer> servers = new CopyOnWriteArrayList<>();

    /** Register a {@link WsServer}. Paths are matched in registration order (first match wins). */
    public void register(WsServer server) {
        servers.add(server);
    }

    /**
     * Find the first {@link WsServer} whose path pattern matches {@code requestPath}.
     *
     * @return the matching server, or {@code null} if no registered server matches
     */
    public WsServer findMatch(String requestPath) {
        for (WsServer s : servers) {
            if (s.matches(requestPath)) return s;
        }
        return null;
    }

    /** {@code true} if no WebSocket endpoints have been registered. */
    public boolean isEmpty() { return servers.isEmpty(); }

    /** Return an unmodifiable snapshot of all registered servers. */
    public List<WsServer> servers() { return List.copyOf(servers); }
}
