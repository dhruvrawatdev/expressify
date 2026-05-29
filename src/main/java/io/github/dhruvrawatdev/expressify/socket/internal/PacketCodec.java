package io.github.dhruvrawatdev.expressify.socket.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes and decodes Socket.IO v5 packets (text, no binary attachments).
 *
 * <p>The Engine.IO message wrapper (type {@code 4}) is handled by {@link EngineSocket};
 * this codec only deals with the Socket.IO payload that sits <em>inside</em> an EIO message.
 */
public final class PacketCodec {

    private static final Logger log = LoggerFactory.getLogger(PacketCodec.class);

    private PacketCodec() {}

    // ── Encode ────────────────────────────────────────────────────────────────

    /** Encode a packet to its Socket.IO wire string (no EIO prefix). */
    public static String encode(Packet p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.type.value);

        if (!"/".equals(p.namespace)) {
            sb.append(p.namespace).append(',');
        }

        if (p.id != null) {
            sb.append(p.id);
        }

        if (!p.data.isEmpty()) {
            sb.append(toJson(p.data));
        }

        return sb.toString();
    }

    /**
     * Encode a CONNECT_ERROR packet.
     * Wire format: {@code 4{"message":"..."}} (object, not array).
     */
    public static String encodeConnectError(String namespace, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append(PacketType.CONNECT_ERROR.value);
        if (!"/".equals(namespace)) sb.append(namespace).append(',');
        sb.append(toJson(Map.of("message", message)));
        return sb.toString();
    }

    /**
     * Encode a CONNECT acknowledgement packet.
     * Wire format: {@code 0{"sid":"..."}} (object, not array).
     */
    public static String encodeConnectAck(String namespace, String sid) {
        StringBuilder sb = new StringBuilder();
        sb.append(PacketType.CONNECT.value);
        if (!"/".equals(namespace)) sb.append(namespace).append(',');
        sb.append(toJson(Map.of("sid", sid)));
        return sb.toString();
    }

    // ── Decode ────────────────────────────────────────────────────────────────

    /**
     * Decode the Socket.IO payload from an Engine.IO MESSAGE frame.
     * {@code raw} is the string content of a WebSocket text frame (no EIO type prefix —
     * {@link EngineSocket} strips the leading {@code '4'} before calling this method).
     */
    public static Packet decode(String raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("Empty packet");

        int i = 0;

        // 1. Packet type
        PacketType type = PacketType.of(raw.charAt(i++) - '0');

        // 2. Namespace
        String namespace = "/";
        if (i < raw.length() && raw.charAt(i) == '/') {
            int comma = raw.indexOf(',', i);
            if (comma == -1) {
                namespace = raw.substring(i);
                i = raw.length();
            } else {
                namespace = raw.substring(i, comma);
                i = comma + 1;
            }
        }

        // 3. Ack id (optional run of digits)
        Integer ackId = null;
        int ackStart = i;
        while (i < raw.length() && Character.isDigit(raw.charAt(i))) i++;
        if (i > ackStart) {
            ackId = Integer.parseInt(raw, ackStart, i, 10);
        }

        // 4. JSON data
        List<Object> data = new ArrayList<>();
        if (i < raw.length()) {
            String json = raw.substring(i);
            try {
                data = parseData(json);
            } catch (Exception e) {
                log.warn("Failed to parse Socket.IO packet data: {}", json, e);
            }
        }

        return new Packet(type, namespace, ackId, data);
    }

    // ── JSON parse (decode path only) ─────────────────────────────────────────

    /**
     * Parse a JSON array or object into a {@code List<Object>}.
     * Arrays are returned as-is; objects are wrapped in a single-element list.
     */
    private static List<Object> parseData(String json) {
        if (json == null || json.isEmpty()) return List.of();
        int[] pos = {0};
        skipWs(json, pos);
        char first = json.charAt(pos[0]);
        if (first == '[') return parseArray(json, pos);
        if (first == '{') return List.of(parseObject(json, pos));
        return List.of();
    }

    private static List<Object> parseArray(String s, int[] pos) {
        pos[0]++; // skip '['
        List<Object> list = new ArrayList<>();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == ']') { pos[0]++; return list; }
        list.add(parseValue(s, pos));
        skipWs(s, pos);
        while (pos[0] < s.length() && s.charAt(pos[0]) == ',') {
            pos[0]++; // skip ','
            skipWs(s, pos);
            list.add(parseValue(s, pos));
            skipWs(s, pos);
        }
        if (pos[0] < s.length()) pos[0]++; // skip ']'
        return list;
    }

    private static Map<String, Object> parseObject(String s, int[] pos) {
        pos[0]++; // skip '{'
        Map<String, Object> map = new LinkedHashMap<>();
        skipWs(s, pos);
        if (pos[0] < s.length() && s.charAt(pos[0]) == '}') { pos[0]++; return map; }
        do {
            skipWs(s, pos);
            String key = parseString(s, pos);
            skipWs(s, pos);
            if (pos[0] < s.length()) pos[0]++; // skip ':'
            skipWs(s, pos);
            map.put(key, parseValue(s, pos));
            skipWs(s, pos);
        } while (pos[0] < s.length() && s.charAt(pos[0]) == ',' && ++pos[0] > 0);
        if (pos[0] < s.length()) pos[0]++; // skip '}'
        return map;
    }

    private static Object parseValue(String s, int[] pos) {
        if (pos[0] >= s.length()) return null;
        char c = s.charAt(pos[0]);
        if (c == '"') return parseString(s, pos);
        if (c == '[') return parseArray(s, pos);
        if (c == '{') return parseObject(s, pos);
        if (c == 't') { pos[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { pos[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { pos[0] += 4; return null; }
        return parseNumber(s, pos);
    }

    private static String parseString(String s, int[] pos) {
        pos[0]++; // skip opening '"'
        StringBuilder sb = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') break;
            if (c == '\\' && pos[0] < s.length()) {
                char esc = s.charAt(pos[0]++);
                switch (esc) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u': {
                        int end = Math.min(pos[0] + 4, s.length());
                        sb.append((char) Integer.parseInt(s.substring(pos[0], end), 16));
                        pos[0] = end;
                        break;
                    }
                    default: sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Number parseNumber(String s, int[] pos) {
        int start = pos[0];
        boolean decimal = false;
        if (pos[0] < s.length() && s.charAt(pos[0]) == '-') pos[0]++;
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (Character.isDigit(c)) { pos[0]++; continue; }
            if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { decimal = true; pos[0]++; continue; }
            break;
        }
        String num = s.substring(start, pos[0]);
        if (decimal) return Double.parseDouble(num);
        try { return Long.parseLong(num); } catch (NumberFormatException e) { return Double.parseDouble(num); }
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length() && s.charAt(pos[0]) <= ' ') pos[0]++;
    }

    // Helpers

    static String toJson(Object value) {
        StringBuilder sb = new StringBuilder(64);
        appendValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof String s) {
            appendString(sb, s);
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                appendValue(sb, list.get(i));
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                appendValue(sb, e.getValue());
            }
            sb.append('}');
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
