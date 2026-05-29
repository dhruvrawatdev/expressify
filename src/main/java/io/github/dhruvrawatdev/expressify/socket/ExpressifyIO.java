package io.github.dhruvrawatdev.expressify.socket;

import io.github.dhruvrawatdev.expressify.Expressify;
import io.github.dhruvrawatdev.expressify.socket.internal.IoUpgradeHandler;
import io.undertow.server.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Socket.IO server for Expressify — mirrors the Node.js {@code io} object.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * Expressify app = new Expressify();
 * ExpressifyIO io = new ExpressifyIO();
 * io.attach(app);
 *
 * io.on("connection", socket -> {
 *     System.out.println("connected: " + socket.id());
 *
 *     socket.on("chat message", args -> {
 *         io.emit("chat message", args[0]);
 *     });
 *
 *     socket.on("disconnect", args -> System.out.println("disconnected"));
 * });
 *
 * app.listen(3000);
 * }</pre>
 *
 * <h2>Custom namespace</h2>
 * <pre>{@code
 * Namespace admin = io.of("/admin");
 * admin.use((socket, next) -> {
 *     if (!isAdmin(socket.handshake().auth())) throw new RuntimeException("Forbidden");
 *     next.run();
 * });
 * admin.on("connection", socket -> socket.emit("hello", "admin!"));
 * }</pre>
 *
 * <h2>Rooms and broadcasting</h2>
 * <pre>{@code
 * socket.join("room1");
 * io.to("room1").emit("news", "Hello room 1!");
 * socket.to("room1").emit("news", "Hello from " + socket.id());
 * socket.broadcast().emit("user joined", socket.id());
 * }</pre>
 *
 * <h2>Acknowledgements</h2>
 * <pre>{@code
 * socket.emitWithAck("ping", args -> System.out.println("pong: " + Arrays.toString(args)));
 * socket.emitWithAck("getData").thenAccept(args -> process(args[0]));
 * }</pre>
 *
 * <h2>Client-side (JavaScript)</h2>
 * <pre>{@code
 * const socket = io("http://localhost:3000");
 * socket.on("connect",  ()  => console.log(socket.id));
 * socket.on("message", msg  => console.log(msg));
 * socket.emit("chat message", "hello server");
 * }</pre>
 */
public final class ExpressifyIO {

    private static final Logger log = LoggerFactory.getLogger(ExpressifyIO.class);

    private final IoServerOptions             options;
    private final Map<String, Namespace>      namespaces = new ConcurrentHashMap<>();
    private final Namespace                   mainNs;
    private volatile IoUpgradeHandler         upgradeHandler = null;


    public ExpressifyIO() {
        this(IoServerOptions.DEFAULT);
    }

    public ExpressifyIO(IoServerOptions options) {
        this.options = options;
        this.mainNs  = new Namespace("/");
        namespaces.put("/", mainNs);
    }

    // Attach

    /**
     * Attach this Socket.IO server to an {@link Expressify} application.
     * Must be called before {@code app.listen()}.
     *
     * <pre>{@code
     * ExpressifyIO io = new ExpressifyIO();
     * io.attach(app);
     * app.listen(3000);
     * }</pre>
     */
    public ExpressifyIO attach(Expressify app) {
        app.ioAttach(this);
        return this;
    }

    /**
     * Create the Undertow upgrade handler, chaining {@code next} as the fallback.
     * Called by {@link Expressify#listen} at server start — not for direct use.
     */
    public HttpHandler createUpgradeHandler(HttpHandler next) {
        upgradeHandler = new IoUpgradeHandler(options, this::resolveNamespace, next);
        return upgradeHandler;
    }

    // Namespace

    /**
     * Get or create a named namespace.
     * Equivalent to {@code io.of("/admin")} in Node.js Socket.IO.
     *
     * <pre>{@code
     * Namespace admin = io.of("/admin");
     * admin.on("connection", socket -> { ... });
     * }</pre>
     */
    public Namespace of(String name) {
        String normalized = name.startsWith("/") ? name : "/" + name;
        return namespaces.computeIfAbsent(normalized, Namespace::new);
    }

    // Main namespace shortcuts

    /**
     * Register a {@code "connection"} listener on the default namespace {@code "/"}.
     *
     * <pre>{@code
     * io.on("connection", socket -> {
     *     socket.emit("welcome");
     * });
     * }</pre>
     */
    public ExpressifyIO on(String event, Consumer<IoSocket> listener) {
        mainNs.on(event, listener);
        return this;
    }

    /**
     * Register middleware on the default namespace {@code "/"}.
     * Equivalent to {@code io.use((socket, next) => { })} in Node.js.
     */
    public ExpressifyIO use(IoMiddleware middleware) {
        mainNs.use(middleware);
        return this;
    }

    /**
     * Emit an event to all connected clients on the default namespace.
     *
     * <pre>{@code
     * io.emit("announcement", "Server maintenance in 5 minutes");
     * }</pre>
     */
    public void emit(String event, Object... args) {
        mainNs.emit(event, args);
    }

    /**
     * Return a {@link BroadcastOperator} targeting specific rooms on the default namespace.
     *
     * <pre>{@code
     * io.to("room1").emit("news", data);
     * }</pre>
     */
    public BroadcastOperator to(String... rooms) {
        return mainNs.to(rooms);
    }

    /** Alias for {@link #to(String...)}. */
    public BroadcastOperator in(String... rooms) { return to(rooms); }

    /**
     * Return a {@link BroadcastOperator} that excludes the given rooms.
     */
    public BroadcastOperator except(String... rooms) {
        return mainNs.except(rooms);
    }

    // Queries

    /** Return all sockets connected to the default namespace. */
    public java.util.Collection<IoSocket> fetchSockets() {
        return mainNs.sockets().values();
    }

    /**
     * Make all sockets on the default namespace join the given rooms.
     * Equivalent to {@code io.socketsJoin("room")} in Node.js Socket.IO.
     *
     * @param rooms room names to join
     */
    public void socketsJoin(String... rooms) { mainNs.socketsJoin(rooms); }

    /**
     * Make all sockets on the default namespace leave the given rooms.
     * Equivalent to {@code io.socketsLeave("room")} in Node.js Socket.IO.
     *
     * @param rooms room names to leave
     */
    public void socketsLeave(String... rooms) { mainNs.socketsLeave(rooms); }

    /**
     * Disconnect all sockets on the default namespace.
     * Equivalent to {@code io.disconnectSockets(close)} in Node.js Socket.IO.
     *
     * @param close if {@code true}, also close the underlying transport
     */
    public void disconnectSockets(boolean close) { mainNs.disconnectSockets(close); }

    /** Return the default namespace {@code "/"}. */
    public Namespace mainNamespace() { return mainNs; }

    // Lifecycle

    /**
     * Shut down the Socket.IO server (stops heartbeat threads).
     * Call when the Expressify application stops.
     */
    public void close() {
        if (upgradeHandler != null) upgradeHandler.shutdown();
    }

    // Internal

    /** Called by {@link IoUpgradeHandler} to resolve a namespace by name. Returns {@code null} for unknown namespaces. */
    Namespace resolveNamespace(String name) {
        return namespaces.get(name);
    }
}
