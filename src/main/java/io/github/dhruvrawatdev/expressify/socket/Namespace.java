package io.github.dhruvrawatdev.expressify.socket;

import io.github.dhruvrawatdev.expressify.socket.internal.InMemoryAdapter;
import io.github.dhruvrawatdev.expressify.socket.internal.Packet;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketCodec;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A Socket.IO namespace — groups sockets under a common path.
 *
 * <p>The default namespace is {@code "/"}. Custom namespaces are created via
 * {@code io.of("/admin")}.
 *
 * <pre>{@code
 * Namespace admin = io.of("/admin");
 * admin.use((socket, next) -> {
 *     if (!isAdmin(socket.handshake().auth())) throw new RuntimeException("Forbidden");
 *     next.run();
 * });
 * admin.on("connection", socket -> {
 *     socket.emit("hello", "admin!");
 * });
 * }</pre>
 */
public final class Namespace {

    private static final Logger log = LoggerFactory.getLogger(Namespace.class);

    private final String namespaceName;
    private final InMemoryAdapter adapter;
    private final Map<String, IoSocket> sockets = new ConcurrentHashMap<>();
    private final List<IoMiddleware> middlewares= new CopyOnWriteArrayList<>();
    private final List<Consumer<IoSocket>> connListeners = new CopyOnWriteArrayList<>();

    Namespace(String name) {
        this.namespaceName = name.startsWith("/") ? name : "/" + name;
        this.adapter = new InMemoryAdapter();
    }

    // Middleware & events

    /**
     * Register a middleware function run for every new socket connection.
     * Equivalent to {@code namespace.use((socket, next) => { })} in Node.js.
     */
    public Namespace use(IoMiddleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    /**
     * Register a {@code "connection"} event listener.
     * Equivalent to {@code namespace.on("connection", socket => { })} in Node.js.
     *
     * <pre>{@code
     * io.on("connection", socket -> {
     *     socket.emit("welcome");
     * });
     * }</pre>
     */
    public Namespace on(String event, Consumer<IoSocket> listener) {
        if ("connection".equals(event) || "connect".equals(event)) {
            connListeners.add(listener);
        } else {
            log.warn("Namespace.on() only supports 'connection'/'connect' events. Got: {}", event);
        }
        return this;
    }

    // Broadcasting

    /**
     * Emit an event to all connected sockets in this namespace.
     *
     * <pre>{@code
     * io.emit("announcement", "Server will restart in 5 minutes");
     * }</pre>
     */
    public void emit(String event, Object... args) {
        to().emit(event, args);
    }

    /**
     * Return a {@link BroadcastOperator} targeting specific rooms.
     *
     * <pre>{@code
     * io.to("room1", "room2").emit("news", data);
     * }</pre>
     */
    public BroadcastOperator to(String... rooms) {
        return new BroadcastOperator(this, adapter, Set.of(rooms), Set.of());
    }

    /** Return a broadcast operator targeting all sockets (no room filter). */
    BroadcastOperator to() {
        return new BroadcastOperator(this, adapter, Set.of(), Set.of());
    }

    /**
     * Return a {@link BroadcastOperator} that excludes the given rooms.
     */
    public BroadcastOperator except(String... rooms) {
        return new BroadcastOperator(this, adapter, Set.of(), Set.of(rooms));
    }

    // Socket queries

    /** Return all currently connected sockets in this namespace. */
    public Map<String, IoSocket> sockets() { return Collections.unmodifiableMap(sockets); }

    /** Return the socket with the given ID, or {@code null}. */
    public IoSocket getSocket(String id) { return sockets.get(id); }

    /** Number of currently connected sockets. */
    public int socketsCount() { return sockets.size(); }

    /**
     * Make all sockets in this namespace join the given rooms.
     * Equivalent to {@code io.socketsJoin("room")} in Node.js Socket.IO.
     *
     * @param rooms room names to join
     */
    public void socketsJoin(String... rooms) { to().socketsJoin(rooms); }

    /**
     * Make all sockets in this namespace leave the given rooms.
     * Equivalent to {@code io.socketsLeave("room")} in Node.js Socket.IO.
     *
     * @param rooms room names to leave
     */
    public void socketsLeave(String... rooms) { to().socketsLeave(rooms); }

    /**
     * Disconnect all sockets in this namespace.
     * Equivalent to {@code io.disconnectSockets(close)} in Node.js Socket.IO.
     *
     * @param close if {@code true}, also close the underlying transport
     */
    public void disconnectSockets(boolean close) { to().disconnectSockets(close); }

    /** Return all known room names in this namespace. */
    public Set<String> allRooms() { return adapter.allRooms(); }

    /** Namespace path (e.g. {@code "/"} or {@code "/admin"}). */
    public String name() { return namespaceName; }

    /**
     * Returns the in-memory room/socket adapter for this namespace.
     *
     * @apiNote Framework-internal — used by {@link BroadcastOperator} and {@code IoUpgradeHandler}.
     */
    public InMemoryAdapter adapter() { return adapter; }

    // Internal — called by IoUpgradeHandler

    /**
     * Accept a new socket into this namespace, run the middleware chain, then fire the
     * {@code "connection"} event on success.
     *
     * @param socket the socket to register
     * @apiNote Framework-internal — called by {@code IoUpgradeHandler} after the CONNECT packet.
     */
    public void addSocket(IoSocket socket) {
        sockets.put(socket.id(), socket);
        runMiddleware(socket, 0, () -> fireConnection(socket));
    }

    /**
     * Remove a socket from this namespace's tracked set.
     *
     * @param id the socket ID to remove
     * @apiNote Framework-internal — called by {@link IoSocket#onDisconnect(String)}.
     */
    public void removeSocket(String id) {
        sockets.remove(id);
    }

    private void runMiddleware(IoSocket socket, int index, Runnable done) {
        if (index >= middlewares.size()) {
            done.run();
            return;
        }
        IoMiddleware mw = middlewares.get(index);
        try {
            mw.handle(socket, () -> runMiddleware(socket, index + 1, done));
        } catch (Exception e) {
            log.warn("Middleware rejected socket {}: {}", socket.id(), e.getMessage());
            String encoded = PacketCodec.encodeConnectError(namespaceName, e.getMessage());
            socket.engine().sendMessage(encoded);
            socket.engine().close();
            sockets.remove(socket.id());
        }
    }

    private void fireConnection(IoSocket socket) {
        // Send CONNECT ack to client
        String ack = PacketCodec.encodeConnectAck(namespaceName, socket.id());
        socket.engine().sendMessage(ack);

        for (Consumer<IoSocket> l : connListeners) {
            try { l.accept(socket); }
            catch (Exception e) { log.error("Error in connection handler for ns {}", namespaceName, e); }
        }
    }
}
