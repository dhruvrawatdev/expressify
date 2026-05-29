package io.github.dhruvrawatdev.expressify.socket;

import io.github.dhruvrawatdev.expressify.socket.internal.InMemoryAdapter;
import io.github.dhruvrawatdev.expressify.socket.internal.Packet;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketCodec;
import io.github.dhruvrawatdev.expressify.socket.internal.PacketType;

import java.util.*;

/**
 * Fluent operator for targeting a subset of sockets when broadcasting.
 *
 * <p>Mirrors the Node.js Socket.IO {@code BroadcastOperator}:
 * <pre>{@code
 * io.to("room1").to("room2").except("room3").emit("news", payload);
 * socket.broadcast.to("room1").emit("hello", "world");
 * }</pre>
 *
 * <p>Java equivalent:
 * <pre>{@code
 * io.to("room1", "room2").except("room3").emit("news", payload);
 * socket.broadcast().to("room1").emit("hello", "world");
 * }</pre>
 */
public final class BroadcastOperator {

    private final Namespace namespace;
    private final InMemoryAdapter adapter;
    private final Set<String> rooms;
    private final Set<String> exceptRooms;

    BroadcastOperator(Namespace namespace, InMemoryAdapter adapter,
                      Set<String> rooms, Set<String> exceptRooms) {
        this.namespace = namespace;
        this.adapter = adapter;
        this.rooms = new HashSet<>(rooms);
        this.exceptRooms = new HashSet<>(exceptRooms);
    }

    // Room targeting

    /**
     * Add one or more rooms to the target set.
     * Equivalent to {@code .to("room1", "room2")} in Node.js Socket.IO.
     *
     * <pre>{@code
     * io.to("room1").to("room2").emit("update", data);
     * }</pre>
     *
     * @param roomNames room names to include in the broadcast target
     * @return a new {@link BroadcastOperator} with the added rooms
     */
    public BroadcastOperator to(String... roomNames) {
        Set<String> merged = new HashSet<>(this.rooms);
        merged.addAll(Arrays.asList(roomNames));
        return new BroadcastOperator(namespace, adapter, merged, exceptRooms);
    }

    /**
     * Alias for {@link #to(String...)}.
     *
     * @param roomNames room names to include
     * @return a new {@link BroadcastOperator} with the added rooms
     */
    public BroadcastOperator in(String... roomNames) { return to(roomNames); }

    /**
     * Exclude sockets in the specified rooms from receiving the broadcast.
     * Equivalent to {@code .except("room")} in Node.js Socket.IO.
     *
     * <pre>{@code
     * io.to("room1").except("room2").emit("news", data);
     * }</pre>
     *
     * @param roomNames room names whose members should be excluded
     * @return a new {@link BroadcastOperator} with the added exclusions
     */
    public BroadcastOperator except(String... roomNames) {
        Set<String> merged = new HashSet<>(this.exceptRooms);
        merged.addAll(Arrays.asList(roomNames));
        return new BroadcastOperator(namespace, adapter, rooms, merged);
    }

    // Emit

    /**
     * Emit an event to all targeted sockets.
     * Equivalent to {@code io.to("room").emit("event", ...args)} in Node.js Socket.IO.
     *
     * <pre>{@code
     * io.to("room1").emit("update", Map.of("key", "value"));
     * io.to("room1", "room2").except("room3").emit("news", payload);
     * }</pre>
     *
     * @param event the event name
     * @param args  zero or more arguments serialized as JSON and delivered to every matching socket
     */
    public void emit(String event, Object... args) {
        List<Object> data = new ArrayList<>();
        data.add(event);
        data.addAll(Arrays.asList(args));

        Packet packet = new Packet(PacketType.EVENT, namespace.name(), null, data);
        String encoded = PacketCodec.encode(packet);

        Set<String> targetIds = resolveTargetIds();
        for (String sid : targetIds) {
            IoSocket socket = namespace.getSocket(sid);
            if (socket != null && socket.connected()) {
                socket.engine().sendMessage(encoded);
            }
        }
    }

    // Socket queries

    /**
     * Return the {@link IoSocket} instances that would receive a broadcast from this operator.
     * Equivalent to {@code fetchSockets()} in Node.js Socket.IO.
     *
     * @return list of connected sockets matching the current room/except filters
     */
    public List<IoSocket> fetchSockets() {
        Set<String> ids = resolveTargetIds();
        List<IoSocket> result = new ArrayList<>(ids.size());
        for (String sid : ids) {
            IoSocket s = namespace.getSocket(sid);
            if (s != null && s.connected()) result.add(s);
        }
        return result;
    }

    /**
     * Disconnect all targeted sockets.
     * Equivalent to {@code disconnectSockets(close)} in Node.js Socket.IO.
     *
     * @param close if {@code true}, also close the underlying Engine.IO transport connection
     */
    public void disconnectSockets(boolean close) {
        for (IoSocket s : fetchSockets()) s.disconnect(close);
    }

    /**
     * Make all targeted sockets join the specified rooms.
     * Equivalent to {@code socketsJoin()} in Node.js Socket.IO.
     *
     * @param roomNames room names the targeted sockets should join
     */
    public void socketsJoin(String... roomNames) {
        for (IoSocket s : fetchSockets()) s.join(roomNames);
    }

    /**
     * Make all targeted sockets leave the specified rooms.
     * Equivalent to {@code socketsLeave()} in Node.js Socket.IO.
     *
     * @param roomNames room names the targeted sockets should leave
     */
    public void socketsLeave(String... roomNames) {
        for (IoSocket s : fetchSockets()) {
            for (String r : roomNames) s.leave(r);
        }
    }

    // Internal

    private Set<String> resolveTargetIds() {
        Set<String> target = rooms.isEmpty()
                ? adapter.allSocketIds()
                : adapter.socketsIn(rooms, exceptRooms);
        if (!rooms.isEmpty() && !exceptRooms.isEmpty()) return target; // already filtered
        if (!exceptRooms.isEmpty()) {
            // No positive rooms — broadcast all except the excluded rooms
            Set<String> excluded = adapter.socketsIn(exceptRooms, null);
            Set<String> result = new HashSet<>(target);
            result.removeAll(excluded);
            return result;
        }
        return target;
    }
}
