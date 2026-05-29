package io.github.dhruvrawatdev.expressify.socket.internal;

import java.util.List;

/**
 * A parsed Socket.IO v5 packet.
 *
 * <p>Wire format (text, no binary attachments):
 * {@code [sio_type][/namespace,][ackId][json_data]}
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code 40} — CONNECT to default namespace</li>
 *   <li>{@code 40/admin,} — CONNECT to /admin</li>
 *   <li>{@code 42["hello","world"]} — EVENT on /, no ack</li>
 *   <li>{@code 42/chat,15["msg","hi"]} — EVENT on /chat, ackId=15</li>
 *   <li>{@code 431["ok"]} — ACK for id=1 on /</li>
 * </ul>
 */
public final class Packet {

    public final PacketType type;
    /** Namespace — always starts with {@code /}. Default: {@code "/"}. */
    public final String namespace;
    /** Ack id — {@code null} when no acknowledgement is expected. */
    public final Integer id;
    /** Payload: event name at index 0, args at 1+. May be empty for CONNECT/DISCONNECT. */
    public final List<Object> data;

    public Packet(PacketType type, String namespace, Integer id, List<Object> data) {
        this.type = type;
        this.namespace = namespace == null ? "/" : namespace;
        this.id = id;
        this.data = data != null ? data : List.of();
    }

    /** Convenience constructor for CONNECT / DISCONNECT with no data. */
    public Packet(PacketType type, String namespace) {
        this(type, namespace, null, null);
    }
}
