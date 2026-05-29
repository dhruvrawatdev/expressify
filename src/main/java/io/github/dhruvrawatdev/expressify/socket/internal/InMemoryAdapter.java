package io.github.dhruvrawatdev.expressify.socket.internal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory room/socket adapter.
 *
 * <p>Maintains two indexes:
 * <ul>
 *   <li>{@code rooms} — room name → set of socket IDs in that room</li>
 *   <li>{@code sids}  — socket ID → set of rooms that socket has joined</li>
 * </ul>
 *
 * <p>Each socket is automatically placed into a room named after its own ID (private room).
 * This is the same behaviour as the official Socket.IO in-memory adapter.
 */
public final class InMemoryAdapter {

    /** room → socketIds */
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    /** socketId → rooms */
    private final Map<String, Set<String>> sids = new ConcurrentHashMap<>();

    // Join / Leave

    /** Add {@code socketId} to {@code room}. Creates the room if it does not exist. */
    public void join(String socketId, String room) {
        rooms.computeIfAbsent(room, k -> ConcurrentHashMap.newKeySet()).add(socketId);
        sids.computeIfAbsent(socketId, k -> ConcurrentHashMap.newKeySet()).add(room);
    }

    /** Remove {@code socketId} from {@code room}. Deletes the room if it becomes empty. */
    public void leave(String socketId, String room) {
        Set<String> inRoom = rooms.get(room);
        if (inRoom != null) {
            inRoom.remove(socketId);
            if (inRoom.isEmpty()) rooms.remove(room);
        }
        Set<String> myRooms = sids.get(socketId);
        if (myRooms != null) myRooms.remove(room);
    }

    /** Remove {@code socketId} from all rooms and delete its tracking entry. */
    public void remove(String socketId) {
        Set<String> myRooms = sids.remove(socketId);
        if (myRooms == null) return;
        for (String room : myRooms) {
            Set<String> inRoom = rooms.get(room);
            if (inRoom != null) {
                inRoom.remove(socketId);
                if (inRoom.isEmpty()) rooms.remove(room);
            }
        }
    }

    // Query

    /** Return all socket IDs in the given rooms (union), optionally excluding some. */
    public Set<String> socketsIn(Set<String> targetRooms, Set<String> exceptRooms) {
        Set<String> result = new HashSet<>();
        for (String room : targetRooms) {
            Set<String> inRoom = rooms.get(room);
            if (inRoom != null) result.addAll(inRoom);
        }
        if (exceptRooms != null && !exceptRooms.isEmpty()) {
            for (String exRoom : exceptRooms) {
                Set<String> inExRoom = rooms.get(exRoom);
                if (inExRoom != null) result.removeAll(inExRoom);
            }
        }
        return result;
    }

    /** Return the set of rooms joined by {@code socketId} (read-only snapshot). */
    public Set<String> roomsOf(String socketId) {
        Set<String> r = sids.get(socketId);
        return r == null ? Set.of() : Collections.unmodifiableSet(r);
    }

    /** Return all room names known to this adapter. */
    public Set<String> allRooms() {
        return Collections.unmodifiableSet(rooms.keySet());
    }

    /** Return all socket IDs tracked by this adapter. */
    public Set<String> allSocketIds() {
        return Collections.unmodifiableSet(sids.keySet());
    }
}
