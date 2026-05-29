package io.github.dhruvrawatdev.expressify.socket;

import io.github.dhruvrawatdev.expressify.socket.internal.EngineSocket;
import io.github.dhruvrawatdev.expressify.socket.internal.InMemoryAdapter;
import io.github.dhruvrawatdev.expressify.socket.internal.Packet;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketCodec;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a Socket.IO connection to a single client on a specific namespace.
 *
 * <p>Mirrors the Node.js {@code socket} object in a Socket.IO connection handler:
 * <pre>{@code
 * io.on("connection", socket -> {
 *     socket.emit("hello", "world");
 *     socket.on("chat message", args -> {
 *         io.emit("chat message", args[0]);
 *     });
 *     socket.on("disconnect", args -> System.out.println("user disconnected"));
 * });
 * }</pre>
 */
public final class IoSocket {

    private static final Logger log = LoggerFactory.getLogger(IoSocket.class);

    private final String id;
    private final Namespace namespace;
    private final EngineSocket engine;
    private final InMemoryAdapter adapter;
    private final Handshake handshake;
    private final Map<String, Object> userData = new ConcurrentHashMap<>();

    private final Map<String, List<Consumer<Object[]>>> listeners = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, Object[]>> anyListeners = new CopyOnWriteArrayList<>();
    private final Map<Integer, AckCallback> ackCallbacks = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Object[]>> pendingFutures = new ConcurrentHashMap<>();
    private final AtomicInteger ackIdCounter = new AtomicInteger(0);
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicBoolean disconnectFired = new AtomicBoolean(false);

    public IoSocket(String id, Namespace namespace, EngineSocket engine,
             InMemoryAdapter adapter, Handshake handshake) {
        this.id = id;
        this.namespace = namespace;
        this.engine = engine;
        this.adapter = adapter;
        this.handshake = handshake;

        // Auto-join own-id room
        adapter.join(id, id);
    }

    // Event listeners

    /**
     * Register a listener for the named event.
     * Equivalent to {@code socket.on("event", (...args) => { })} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.on("chat message", args -> System.out.println(args[0]));
     * socket.on("order", args -> processOrder((Map<?,?>) args[0]));
     * }</pre>
     *
     * @param event    the event name to listen for (e.g. {@code "chat message"}, {@code "disconnect"})
     * @param listener called with the event arguments each time the event fires
     * @return this socket for chaining
     */
    public IoSocket on(String event, Consumer<Object[]> listener) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
        return this;
    }

    /**
     * Register a one-time listener — automatically removed after the first invocation.
     * Equivalent to {@code socket.once("event", fn)} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.once("init", args -> socket.emit("ready", "welcome back!"));
     * }</pre>
     *
     * @param event    the event name
     * @param listener called at most once then unregistered
     * @return this socket for chaining
     */
    @SuppressWarnings("unchecked")
    public IoSocket once(String event, Consumer<Object[]> listener) {
        Consumer<Object[]>[] wrapper = new Consumer[1];
        wrapper[0] = args -> {
            off(event, wrapper[0]);
            listener.accept(args);
        };
        return on(event, wrapper[0]);
    }

    /**
     * Remove a previously registered listener for an event.
     * Equivalent to {@code socket.off("event", fn)} in Node.js Socket.IO.
     *
     * @param event    the event name
     * @param listener the exact listener reference originally passed to {@link #on} or {@link #once}
     * @return this socket for chaining
     */
    public IoSocket off(String event, Consumer<Object[]> listener) {
        List<Consumer<Object[]>> l = listeners.get(event);
        if (l != null) l.remove(listener);
        return this;
    }

    /**
     * Remove all listeners for the given event.
     * Equivalent to {@code socket.removeAllListeners("event")} in Node.js Socket.IO.
     *
     * @param event the event whose listeners should be cleared
     * @return this socket for chaining
     */
    public IoSocket offAll(String event) {
        listeners.remove(event);
        return this;
    }

    /**
     * Register a catch-all listener invoked for every incoming event, before named listeners.
     * Equivalent to {@code socket.onAny((event, ...args) => { })} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.onAny((event, args) -> log.debug("event {} args {}", event, Arrays.toString(args)));
     * }</pre>
     *
     * @param listener receives the event name and its arguments
     * @return this socket for chaining
     */
    public IoSocket onAny(BiConsumer<String, Object[]> listener) {
        anyListeners.add(listener);
        return this;
    }

    // Emitting

    /**
     * Emit an event to this client.
     * Equivalent to {@code socket.emit("event", ...args)} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.emit("welcome", "hello!");
     * socket.emit("order", Map.of("id", 1, "status", "pending"));
     * socket.emit("coords", 12.5, 48.2);
     * }</pre>
     *
     * @param event the event name
     * @param args  zero or more arguments serialized as JSON and sent to the client
     * @return this socket for chaining
     */
    public IoSocket emit(String event, Object... args) {
        List<Object> data = buildEventData(event, args);
        Packet pkt = new Packet(PacketType.EVENT, namespace.name(), null, data);
        engine.sendMessage(PacketCodec.encode(pkt));
        return this;
    }

    /**
     * Emit an event and invoke {@code ack} when the client sends an acknowledgement.
     * Equivalent to passing a callback as the last argument in Node.js:
     * {@code socket.emit("getData", payload, (...args) => { })}.
     *
     * <pre>{@code
     * socket.emitWithAck("getData", ack -> process(ack[0]), "param1");
     * socket.emitWithAck("ping", ack -> System.out.println("pong received"));
     * }</pre>
     *
     * @param event the event name
     * @param ack   callback invoked with the client's acknowledgement arguments
     * @param args  zero or more event arguments
     * @return this socket for chaining
     */
    public IoSocket emitWithAck(String event, AckCallback ack, Object... args) {
        int id = ackIdCounter.incrementAndGet();
        ackCallbacks.put(id, ack);
        List<Object> data = buildEventData(event, args);
        Packet pkt = new Packet(PacketType.EVENT, namespace.name(), id, data);
        engine.sendMessage(PacketCodec.encode(pkt));
        return this;
    }

    /**
     * Emit an event and return a {@link CompletableFuture} that completes with the
     * client's acknowledgement arguments.
     *
     * <pre>{@code
     * socket.emitWithAck("ping")
     *       .thenAccept(args -> System.out.println("pong args: " + Arrays.toString(args)));
     *
     * // With extra arguments:
     * socket.emitWithAck("fetchUser", "42")
     *       .thenAccept(args -> System.out.println("user: " + args[0]));
     * }</pre>
     *
     * @param event the event name
     * @param args  zero or more event arguments
     * @return a future that resolves when the client acknowledges
     */
    public CompletableFuture<Object[]> emitWithAck(String event, Object... args) {
        CompletableFuture<Object[]> future = new CompletableFuture<>();
        int ackId = ackIdCounter.incrementAndGet();
        pendingFutures.put(ackId, future);
        ackCallbacks.put(ackId, ackArgs -> {
            pendingFutures.remove(ackId);
            future.complete(ackArgs);
        });
        List<Object> data = buildEventData(event, args);
        Packet pkt = new Packet(PacketType.EVENT, namespace.name(), ackId, data);
        engine.sendMessage(PacketCodec.encode(pkt));
        return future;
    }

    // Rooms

    /**
     * Join one or more rooms.
     * Equivalent to {@code socket.join("room")} in Node.js Socket.IO.
     *
     * <p>After joining, the socket will receive events broadcast to those rooms
     * via {@code io.to("room")} or {@code socket.to("room")}.
     *
     * <pre>{@code
     * socket.join("room1");
     * socket.join("room1", "room2", "room3");
     * }</pre>
     *
     * @param rooms one or more room names to join
     * @return this socket for chaining
     */
    public IoSocket join(String... rooms) {
        for (String room : rooms) adapter.join(id, room);
        return this;
    }

    /**
     * Leave a room.
     * Equivalent to {@code socket.leave("room")} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.leave("room1");
     * }</pre>
     *
     * @param room the room name to leave
     * @return this socket for chaining
     */
    public IoSocket leave(String room) {
        adapter.leave(id, room);
        return this;
    }

    /**
     * Return the set of room names this socket has joined (read-only snapshot).
     * Always includes the socket's own private room (named after its {@link #id()}).
     * Equivalent to {@code socket.rooms} in Node.js Socket.IO.
     *
     * @return unmodifiable set of room names
     */
    public Set<String> rooms() { return adapter.roomsOf(id); }

    // Broadcasting

    /**
     * Return a {@link BroadcastOperator} targeting specific rooms, excluding this socket.
     * Equivalent to {@code socket.to("room1")} in Node.js Socket.IO — the sender is never
     * included in the delivery, even if it has joined the target room.
     *
     * <pre>{@code
     * socket.to("room1").emit("news", "hello");
     * socket.to("room1", "room2").emit("update", data);
     * }</pre>
     *
     * @param rooms one or more room names to target
     * @return a {@link BroadcastOperator} for chaining
     */
    public BroadcastOperator to(String... rooms) {
        // Exclude self — matches Node.js socket.to() which always excludes the sender
        return new BroadcastOperator(namespace, adapter, Set.of(rooms), Set.of(id));
    }

    /**
     * Alias for {@link #to(String...)}.
     *
     * @param rooms one or more room names to target
     * @return a {@link BroadcastOperator} for chaining
     */
    public BroadcastOperator in(String... rooms) { return to(rooms); }

    /**
     * Return a {@link BroadcastOperator} targeting all sockets in the namespace except this one.
     * Equivalent to {@code socket.broadcast} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.broadcast().emit("user joined", socket.id());
     * }</pre>
     *
     * @return a {@link BroadcastOperator} that excludes this socket
     */
    public BroadcastOperator broadcast() {
        return new BroadcastOperator(namespace, adapter, Set.of(), Set.of(id));
    }

    /**
     * Return a {@link BroadcastOperator} that broadcasts to all sockets <em>not</em> in the
     * given rooms (and also excludes this socket itself).
     * Equivalent to {@code socket.except("room")} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.except("room1").emit("update", data);
     * }</pre>
     *
     * @param rooms room names whose members are excluded from the broadcast
     * @return a {@link BroadcastOperator} for chaining
     */
    public BroadcastOperator except(String... rooms) {
        // Exclude both the listed rooms AND self — matches Node.js socket.except() behaviour
        Set<String> excluded = new HashSet<>(Arrays.asList(rooms));
        excluded.add(id);
        return new BroadcastOperator(namespace, adapter, Set.of(), Collections.unmodifiableSet(excluded));
    }

    // Disconnect

    /**
     * Disconnect this socket from its namespace.
     * Equivalent to {@code socket.disconnect(close)} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.disconnect(true);   // disconnect and close transport
     * socket.disconnect(false);  // disconnect namespace only; transport stays open
     * }</pre>
     *
     * @param close if {@code true}, also close the underlying Engine.IO transport (full connection close);
     *              if {@code false}, only disconnect from this namespace
     */
    public void disconnect(boolean close) {
        if (connected.compareAndSet(true, false)) {
            Packet pkt = new Packet(PacketType.DISCONNECT, namespace.name());
            try { engine.sendMessage(PacketCodec.encode(pkt)); } catch (Exception ignored) {}
            if (close) engine.close();
            onDisconnect("server namespace disconnect");
        }
    }

    // Properties

    /**
     * The unique socket ID assigned at connection time.
     * Also used as the name of this socket's private room (for direct messaging).
     * Equivalent to {@code socket.id} in Node.js Socket.IO.
     *
     * @return the socket's unique string identifier
     */
    public String id() { return id; }

    /**
     * Whether this socket is currently connected.
     * Returns {@code false} after {@link #disconnect(boolean)} is called or the transport closes.
     * Equivalent to {@code socket.connected} in Node.js Socket.IO.
     *
     * @return {@code true} if the socket is open and active
     */
    public boolean connected() { return connected.get() && !engine.isClosed(); }

    /**
     * Whether this socket is currently disconnected.
     * Inverse of {@link #connected()}.
     * Equivalent to {@code socket.disconnected} in Node.js Socket.IO.
     *
     * @return {@code true} if the socket has disconnected or the transport is closed
     */
    public boolean disconnected() { return !connected(); }

    /**
     * The namespace this socket is connected to.
     * Equivalent to {@code socket.nsp} in Node.js Socket.IO.
     *
     * @return the {@link Namespace} this socket belongs to
     */
    public Namespace namespace() { return namespace; }

    /**
     * Connection metadata captured at handshake time — headers, query params, auth data, IP.
     * Equivalent to {@code socket.handshake} in Node.js Socket.IO.
     *
     * <pre>{@code
     * Handshake hs = socket.handshake();
     * String token = (String) hs.auth().get("token");
     * String ua    = hs.headers().get("user-agent");
     * String ip    = hs.address();
     * }</pre>
     *
     * @return the immutable {@link Handshake} snapshot for this connection
     */
    public Handshake handshake() { return handshake; }

    /**
     * Arbitrary per-socket data store — attach application-level state here.
     * Equivalent to {@code socket.data} in Node.js Socket.IO.
     *
     * <pre>{@code
     * socket.data().put("username", "Alice");
     * String name = (String) socket.data().get("username");
     * }</pre>
     *
     * @return the mutable data map for this socket
     */
    public Map<String, Object> data() { return userData; }

    // Internal (called by Namespace / IoUpgradeHandler)

    /**
     * Returns the underlying Engine.IO transport.
     *
     * @apiNote Framework-internal — do not call from application code.
     */
    public EngineSocket engine() { return engine; }

    /**
     * Dispatch an incoming decoded packet to this socket's event listeners.
     *
     * @apiNote Framework-internal — called by {@code IoUpgradeHandler}.
     */
    public void dispatch(Packet packet) {
        switch (packet.type) {
            case EVENT -> handleEvent(packet);
            case ACK   -> handleAck(packet);
            case DISCONNECT -> {
                connected.set(false);
                onDisconnect("client namespace disconnect");
            }
            default -> log.debug("Unexpected packet type {} for socket {}", packet.type, id);
        }
    }

    private void handleEvent(Packet packet) {
        if (packet.data.isEmpty()) return;
        String event = packet.data.get(0).toString();
        Object[] args = packet.data.subList(1, packet.data.size()).toArray();

        // If the client expects an ack, wrap args to allow replying
        if (packet.id != null) {
            int ackId = packet.id;
            Object[] argsWithAck = Arrays.copyOf(args, args.length + 1);
            argsWithAck[args.length] = (AckCallback) ackArgs -> sendAck(ackId, ackArgs);
            args = argsWithAck;
        }

        // onAny listeners first
        final Object[] finalArgs = args;
        for (BiConsumer<String, Object[]> l : anyListeners) {
            try { l.accept(event, finalArgs); } catch (Exception e) {
                log.warn("onAny listener error", e);
            }
        }

        // Named listeners
        List<Consumer<Object[]>> named = listeners.get(event);
        if (named != null) {
            for (Consumer<Object[]> l : named) {
                try { l.accept(finalArgs); } catch (Exception e) {
                    log.warn("Listener error for event '{}' on socket {}", event, id, e);
                }
            }
        }
    }

    private void handleAck(Packet packet) {
        if (packet.id == null) return;
        AckCallback cb = ackCallbacks.remove(packet.id);
        if (cb != null) {
            try { cb.call(packet.data.toArray()); }
            catch (Exception e) { log.warn("Ack callback error for id {}", packet.id, e); }
        }
    }

    private void sendAck(int ackId, Object... ackArgs) {
        List<Object> data = new ArrayList<>(Arrays.asList(ackArgs));
        Packet pkt = new Packet(PacketType.ACK, namespace.name(), ackId, data);
        engine.sendMessage(PacketCodec.encode(pkt));
    }

    /**
     * Handle a disconnect event — cleans up adapter state and fires {@code "disconnect"} listeners.
     * This method is idempotent: if called multiple times (e.g. from both {@link #disconnect(boolean)}
     * and the transport close handler), the cleanup and listeners run exactly once.
     *
     * @param reason a human-readable disconnect reason string passed to {@code "disconnect"} listeners
     * @apiNote Framework-internal — called by {@code IoUpgradeHandler} and {@link #disconnect(boolean)}.
     */
    public void onDisconnect(String reason) {
        connected.set(false);
        // Guard: cleanup + listeners must fire exactly once even if called from multiple threads
        // (e.g. server-side disconnect() racing with engine.onClose())
        if (!disconnectFired.compareAndSet(false, true)) return;

        // Complete all pending emitWithAck futures exceptionally so callers don't hang forever
        if (!pendingFutures.isEmpty()) {
            RuntimeException ex = new RuntimeException("Socket disconnected: " + reason);
            pendingFutures.values().forEach(f -> f.completeExceptionally(ex));
            pendingFutures.clear();
        }
        ackCallbacks.clear();

        adapter.remove(id);
        namespace.removeSocket(id);

        List<Consumer<Object[]>> disconnectListeners = listeners.get("disconnect");
        if (disconnectListeners != null) {
            Object[] reasonArg = { reason };
            for (Consumer<Object[]> l : disconnectListeners) {
                try { l.accept(reasonArg); } catch (Exception e) {
                    log.warn("disconnect listener error", e);
                }
            }
        }
    }

    // Helpers

    private static List<Object> buildEventData(String event, Object[] args) {
        List<Object> data = new ArrayList<>(1 + args.length);
        data.add(event);
        data.addAll(Arrays.asList(args));
        return data;
    }

    @Override
    public String toString() { return "IoSocket{id=" + id + ", ns=" + namespace.name() + "}"; }
}
