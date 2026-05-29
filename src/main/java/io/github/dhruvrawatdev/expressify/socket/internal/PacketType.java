package io.github.dhruvrawatdev.expressify.socket.internal;

/**
 * Socket.IO v5 packet types.
 * See: https://socket.io/docs/v4/socket-io-protocol/#packet-types
 */
public enum PacketType {

    CONNECT(0),
    DISCONNECT(1),
    EVENT(2),
    ACK(3),
    CONNECT_ERROR(4),
    BINARY_EVENT(5),
    BINARY_ACK(6);

    public final int value;

    PacketType(int value) { this.value = value; }

    public static PacketType of(int value) {
        for (PacketType t : values()) {
            if (t.value == value) return t;
        }
        throw new IllegalArgumentException("Unknown Socket.IO packet type: " + value);
    }
}
