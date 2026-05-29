package io.github.dhruvrawatdev.expressify.http;

import io.undertow.server.HttpServerExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Express.js-compatible request wrapper around Undertow's {@link HttpServerExchange}.
 *
 * <p>Mirrors the Express.js {@code req} API: params, query, body, headers,
 * cookies, sessions, files, content negotiation, range parsing, and more.
 */
public class Request {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServerExchange exchange;

    private byte[] rawBodyBytes;
    private boolean bodyRead = false;

    private Map<String, List<String>> formFields;

    private boolean textParsed  = false;
    private boolean rawParsed   = false;
    private String textCharset = "utf-8";

    private Map<String, String> params = new HashMap<>();

    private String routingPath;
    private String baseUrl = "";

    private Map<String, Object> sessionData;
    private String sessionId;

    private Map<String, String> signedCookies = new HashMap<>();
    private String secret;
    private String methodOverride;

    private final Map<String, Object> locals = new HashMap<>();

    private Response response;
    private Map<String, Object> appSettings = Collections.emptyMap();

    public Request(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.routingPath = exchange.getRequestPath();
    }

    /** Framework-internal: reuse this instance for a new request on the same worker thread. */
    public void reset(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.routingPath = exchange.getRequestPath();
        this.rawBodyBytes = null;
        this.bodyRead = false;
        this.formFields = null;
        this.textParsed = false;
        this.rawParsed = false;
        this.textCharset = "utf-8";
        this.params = new HashMap<>();
        this.baseUrl = "";
        this.sessionData = null;
        this.sessionId = null;
        this.signedCookies = new HashMap<>();
        this.secret = null;
        this.methodOverride = null;
        this.locals.clear();
        this.response = null;
        this.appSettings = Collections.emptyMap();
    }

    public HttpServerExchange getExchange() { return exchange; }
    public void setParams(Map<String, String> params) { this.params = params; }
    public void setRoutingPath(String path) { this.routingPath = path; }
    public String getRoutingPath() { return routingPath; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setSessionData(Map<String, Object> data, String id) { this.sessionData = data; this.sessionId = id; }
    public String getSessionId() { return sessionId; }
    public void setFormFields(Map<String, List<String>> fields) { this.formFields = fields; }
    public void setTextCharset(String charset) { this.textCharset = charset; }
    public void setTextParsed(boolean v) { this.textParsed = v; }
    public void setRawParsed(boolean v)  { this.rawParsed = v; }
    public void setResponse(Response res) { this.response = res; }
    public void setSettings(Map<String, Object> settings) { this.appSettings = settings != null ? settings : Collections.emptyMap(); }
    public void setSignedCookies(Map<String, String> sc) { this.signedCookies = sc; }
    public void setSecret(String secret) { this.secret = secret; }
    public void setMethodOverride(String method) { this.methodOverride = method; }

    public void overrideBodyBytes(byte[] bytes) {
        this.rawBodyBytes = bytes;
        this.bodyRead = true;
    }

    public byte[] getRawBody() {
        body();
        return rawBodyBytes != null ? rawBodyBytes : new byte[0];
    }

    // Core Properties

    /**
     * HTTP method in uppercase. Reflects any override set by method-override middleware.
     * Use {@link #originalMethod()} to get the original transport-level method.
     */
    public String method() {
        return methodOverride != null ? methodOverride : exchange.getRequestMethod().toString();
    }

    /** Original HTTP method before any method-override, e.g. "POST". */
    public String originalMethod() {
        return exchange.getRequestMethod().toString();
    }

    /** Request path, e.g. "/api/users/123". */
    public String path() { return exchange.getRequestPath(); }

    /** Full original URL including query string. */
    public String originalUrl() {
        String q = exchange.getQueryString();
        String p = exchange.getRequestPath();
        return (q != null && !q.isEmpty()) ? p + "?" + q : p;
    }

    /** Base URL of the router this request matched (empty for root app). */
    public String baseUrl() { return baseUrl; }

    /** Client IP address. Respects X-Forwarded-For when present. */
    public String ip() {
        String xff = header("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        java.net.InetSocketAddress addr = exchange.getSourceAddress();
        if (addr == null) return "unknown";
        java.net.InetAddress inet = addr.getAddress();
        return inet != null ? inet.getHostAddress() : "unknown";
    }

    /** All IPs from X-Forwarded-For (client first, closest proxy last). */
    public List<String> ips() {
        String xff = header("X-Forwarded-For");
        if (xff == null || xff.isBlank()) return Collections.emptyList();
        String[] parts = xff.split(",");
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) list.add(p.trim());
        return Collections.unmodifiableList(list);
    }

    /** Protocol: "http" or "https". Trusts X-Forwarded-Proto. */
    public String protocol() {
        String xfp = header("X-Forwarded-Proto");
        if (xfp != null && !xfp.isBlank()) return xfp.split(",")[0].trim().toLowerCase();
        return "http";
    }

    /** Whether the connection uses HTTPS. */
    public boolean secure() { return "https".equals(protocol()); }

    /** Hostname from the Host header (port stripped). Trusts X-Forwarded-Host. */
    public String hostname() {
        String h = host();
        if (h == null) return null;
        int bracket = h.lastIndexOf(']');
        int colon   = h.indexOf(':', bracket + 1);
        return colon != -1 ? h.substring(0, colon) : h;
    }

    /** Full Host header value (may include port). Trusts X-Forwarded-Host. */
    public String host() {
        String xfh = header("X-Forwarded-Host");
        if (xfh != null && !xfh.isBlank()) return xfh.split(",")[0].trim();
        return header("Host");
    }

    /**
     * Subdomains of the hostname.
     * E.g. "api.users.example.com" → ["users", "api"] (nearest first).
     */
    public List<String> subdomains() {
        String h = hostname();
        if (h == null || h.isEmpty()) return Collections.emptyList();
        if (h.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$") || h.startsWith("[")) return Collections.emptyList();
        String[] parts = h.split("\\.");
        int offset = 2;
        Object o = appSettings.get("subdomain offset");
        if (o instanceof Number n) offset = n.intValue();
        if (parts.length <= offset) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int i = parts.length - offset - 1; i >= 0; i--) result.add(parts[i]);
        return Collections.unmodifiableList(result);
    }

    /** True if the request was issued by XMLHttpRequest. */
    public boolean xhr() {
        String xrw = header("X-Requested-With");
        return "xmlhttprequest".equalsIgnoreCase(xrw);
    }

    /**
     * True if the response ETag or Last-Modified matches the request's conditional headers.
     * Only meaningful for GET/HEAD on 2xx or 304 responses.
     */
    public boolean fresh() {
        String m = method();
        if (!"GET".equals(m) && !"HEAD".equals(m)) return false;
        if (response != null) {
            int status = response.getStatus();
            if (!((status >= 200 && status < 300) || status == 304)) return false;
        }
        String etag = response != null ? response.get("ETag") : null;
        String lm   = response != null ? response.get("Last-Modified") : null;
        String inm  = header("If-None-Match");
        String ims  = header("If-Modified-Since");
        if (inm != null && etag != null) {
            return inm.equals("*") || inm.contains(etag);
        }
        if (ims != null && lm != null) {
            try {
                long clientMs = java.time.ZonedDateTime
                        .parse(ims, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
                long serverMs = java.time.ZonedDateTime
                        .parse(lm,  java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
                return clientMs >= serverMs;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /** True when the cached response is stale (inverse of {@link #fresh()}). */
    public boolean stale() { return !fresh(); }

    // Headers

    /**
     * Get a request header by name (case-insensitive).
     * Special-cases "Referer"/"Referrer" to check both spellings.
     */
    public String get(String name) {
        if (name == null) return null;
        String lc = name.toLowerCase();
        if (lc.equals("referer") || lc.equals("referrer")) {
            String v = exchange.getRequestHeaders().getFirst("Referrer");
            return v != null ? v : exchange.getRequestHeaders().getFirst("Referer");
        }
        return exchange.getRequestHeaders().getFirst(name);
    }

    /** Alias for {@link #get(String)}. */
    public String header(String name) { return get(name); }

    // Content Type

    /**
     * Check if the Content-Type matches one or more types.
     * Accepts full MIME types ("application/json"), wildcards ("text/*"),
     * or short extensions ("json", "html").
     */
    public boolean is(String... types) {
        String ct = get("Content-Type");
        if (ct == null) return false;
        String base = ct.split(";")[0].trim().toLowerCase();
        for (String type : types) {
            String t = type.trim().toLowerCase();
            if (!t.contains("/")) t = extensionToMime(t);
            if (t == null) continue;
            if (t.equals("*/*") || t.equals("*")) return true;
            if (t.endsWith("/*")) {
                if (base.startsWith(t.substring(0, t.length() - 1))) return true;
            } else {
                if (base.equals(t)) return true;
            }
        }
        return false;
    }

    /** Convenience check for {@code Content-Type: application/json}. */
    public boolean isJson() { return is("application/json"); }

    // Content Negotiation

    /**
     * Return the best match from the given types against the Accept header,
     * or {@code null} if none match.
     */
    public String accepts(String... types) {
        String accept = get("Accept");
        if (accept == null || accept.isBlank()) return types.length > 0 ? types[0] : null;
        for (String type : types) {
            if (accept.contains(type) || accept.contains("*/*")) return type;
        }
        return null;
    }

    /** Return the best match from the given encodings against Accept-Encoding, or {@code null}. */
    public String acceptsEncodings(String... encodings) {
        String ae = get("Accept-Encoding");
        if (ae == null || ae.isBlank()) {
            for (String e : encodings) if ("identity".equalsIgnoreCase(e)) return e;
            return null;
        }
        for (String enc : encodings) {
            if (ae.contains(enc) || ae.contains("*")) return enc;
        }
        return null;
    }

    /** Return the best match from the given charsets against Accept-Charset, or {@code null}. */
    public String acceptsCharsets(String... charsets) {
        String ac = get("Accept-Charset");
        if (ac == null || ac.isBlank()) return charsets.length > 0 ? charsets[0] : null;
        for (String cs : charsets) {
            if (ac.contains(cs) || ac.contains("*")) return cs;
        }
        return null;
    }

    /** Return the best match from the given languages against Accept-Language, or {@code null}. */
    public String acceptsLanguages(String... languages) {
        String al = get("Accept-Language");
        if (al == null || al.isBlank()) return languages.length > 0 ? languages[0] : null;
        for (String lang : languages) {
            if (al.contains(lang) || al.contains("*")) return lang;
        }
        return null;
    }

    // Range

    /**
     * Parse the Range header against the given resource {@code size}.
     *
     * @param size total resource size in bytes
     * @return {@code null} if no Range header; {@link RangeResult} with parse result otherwise.
     *         Check {@link RangeResult#isInvalid()} and {@link RangeResult#isUnsatisfiable()}.
     */
    public RangeResult range(long size) {
        return range(size, false);
    }

    /**
     * Parse the Range header with optional range combining.
     *
     * @param size    total resource size in bytes
     * @param combine if true, adjacent/overlapping ranges are merged
     */
    public RangeResult range(long size, boolean combine) {
        String rangeHeader = get("Range");
        if (rangeHeader == null) return null;

        int eq = rangeHeader.indexOf('=');
        if (eq < 0) return RangeResult.invalid();

        String type = rangeHeader.substring(0, eq).trim();
        if (type.isEmpty()) return RangeResult.invalid();

        String[] specs = rangeHeader.substring(eq + 1).split(",");
        List<long[]> ranges = new ArrayList<>();

        for (String spec : specs) {
            spec = spec.trim();
            if (spec.isEmpty()) return RangeResult.invalid();

            int dash = spec.indexOf('-');
            if (dash < 0) return RangeResult.invalid();

            try {
                long start, end;
                if (dash == 0) {
                    long suffix = Long.parseLong(spec.substring(1).trim());
                    if (suffix <= 0) return RangeResult.invalid();
                    start = Math.max(0, size - suffix);
                    end   = size - 1;
                } else if (dash == spec.length() - 1) {
                    start = Long.parseLong(spec.substring(0, dash).trim());
                    end   = size - 1;
                } else {
                    start = Long.parseLong(spec.substring(0, dash).trim());
                    end   = Long.parseLong(spec.substring(dash + 1).trim());
                }
                if (start > end) return RangeResult.invalid();
                if (start >= size) continue;
                if (end >= size) end = size - 1;
                ranges.add(new long[]{start, end});
            } catch (NumberFormatException e) {
                return RangeResult.invalid();
            }
        }

        if (ranges.isEmpty()) return RangeResult.unsatisfiable();

        if (combine) {
            ranges.sort((a, b) -> Long.compare(a[0], b[0]));
            List<long[]> merged = new ArrayList<>();
            long[] cur = ranges.get(0).clone();
            for (int i = 1; i < ranges.size(); i++) {
                long[] next = ranges.get(i);
                if (next[0] <= cur[1] + 1) {
                    cur[1] = Math.max(cur[1], next[1]);
                } else {
                    merged.add(cur);
                    cur = next.clone();
                }
            }
            merged.add(cur);
            ranges = merged;
        }

        return RangeResult.of(type, ranges);
    }

    // Route Params

    /** Named route parameter, e.g. {@code req.params("id")} for route {@code /users/:id}. */
    public String params(String name) { return params.get(name); }

    /** Alias for {@link #params(String)} — Express API parity. */
    public String param(String name) { return params.get(name); }

    /** All route parameters as an immutable map. */
    public Map<String, String> params() { return Collections.unmodifiableMap(params); }

    // Query String

    /** Get a query string parameter. */
    public String query(String name) {
        Deque<String> values = exchange.getQueryParameters().get(name);
        return (values != null && !values.isEmpty()) ? values.peekFirst() : null;
    }

    /** Get a query parameter with a fallback default. */
    public String query(String name, String defaultValue) {
        String v = query(name);
        return v != null ? v : defaultValue;
    }

    /** All query parameters as a map (multi-value keys return first value). */
    public Map<String, String> queryAll() {
        Map<String, String> result = new LinkedHashMap<>();
        exchange.getQueryParameters().forEach((k, v) -> {
            if (!v.isEmpty()) result.put(k, v.peekFirst());
        });
        return Collections.unmodifiableMap(result);
    }

    // Body

    /** Read the raw request body as a UTF-8 String. Returns "" for multipart bodies. */
    public String body() {
        String ct = get("Content-Type");
        if (ct != null && ct.contains("multipart/form-data")) {
            bodyRead = true;
            rawBodyBytes = new byte[0];
            return "";
        }
        if (!bodyRead) {
            bodyRead = true;
            try (InputStream is = exchange.getInputStream()) {
                rawBodyBytes = is.readAllBytes();
            } catch (IOException e) {
                rawBodyBytes = new byte[0];
            }
        }
        return rawBodyBytes == null ? "" : new String(rawBodyBytes, StandardCharsets.UTF_8);
    }

    /**
     * Return the body decoded using the charset set by {@code Expressify.text()} middleware.
     * Returns "" if the text() middleware did not run for this request.
     */
    public String text() {
        if (!textParsed) return "";
        if (rawBodyBytes == null || rawBodyBytes.length == 0) return "";
        try {
            return new String(rawBodyBytes, textCharset);
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(rawBodyBytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * Return the raw request body bytes.
     * Returns an empty array if the raw() middleware did not run for this request.
     */
    public byte[] rawBytes() {
        if (!rawParsed) return new byte[0];
        return rawBodyBytes != null ? rawBodyBytes.clone() : new byte[0];
    }

    /**
     * Get a single field from a URL-encoded or JSON body.
     * For JSON bodies: extracts a top-level string key.
     */
    public String body(String fieldName) {
        if (is("application/json")) {
            try {
                String raw = body();
                if (!raw.isEmpty()) {
                    Map<String, Object> map = OBJECT_MAPPER.readValue(raw, new TypeReference<Map<String, Object>>(){});
                    Object val = map.get(fieldName);
                    return val != null ? val.toString() : null;
                }
            } catch (Exception ignored) {}
            return null;
        }
        ensureFormFields();
        List<String> values = formFields.get(fieldName);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    // Cookies

    /** Get a plain cookie value by name. */
    public String cookie(String name) {
        for (io.undertow.server.handlers.Cookie c : exchange.requestCookies()) {
            if (c.getName().equals(name)) return c.getValue();
        }
        return null;
    }

    /** All cookies as an immutable name→value map. */
    public Map<String, String> cookies() {
        Map<String, String> map = new LinkedHashMap<>();
        for (io.undertow.server.handlers.Cookie c : exchange.requestCookies()) {
            map.putIfAbsent(c.getName(), c.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Signed cookies populated by {@code Expressify.cookieParser(secret)}.
     * Returns an immutable map of verified signed cookie names → decoded values.
     */
    public Map<String, String> signedCookies() {
        return Collections.unmodifiableMap(signedCookies);
    }

    /**
     * Read a signed cookie set with {@code CookieOptions.builder().signed(true)}.
     * Returns the original value, or {@code null} if absent, unsigned, or tampered.
     */
    public String signedCookie(String name, String secret) {
        if (secret == null || secret.isEmpty()) return null;
        String raw = cookie(name);
        if (raw == null || !raw.startsWith("s:")) return null;
        String signed = raw.substring(2);
        int dot = signed.lastIndexOf('.');
        if (dot < 0) return null;
        String value    = signed.substring(0, dot);
        String expected = Response.hmacSign(value, secret);
        String expHash  = expected.substring(value.length() + 1);
        String givenHash = signed.substring(dot + 1);
        return expHash.equals(givenHash) ? value : null;
    }

    /** The cookie secret set by CookieParser middleware. */
    public String secret() { return secret; }

    // Session

    /** The full session data map (set by session middleware). */
    public Map<String, Object> session() {
        if (sessionData == null) sessionData = new HashMap<>();
        return sessionData;
    }

    /** Get a typed session value. */
    @SuppressWarnings("unchecked")
    public <T> T session(String key) {
        return sessionData != null ? (T) sessionData.get(key) : null;
    }

    /** Set a session value. */
    public void session(String key, Object value) {
        if (sessionData == null) sessionData = new HashMap<>();
        sessionData.put(key, value);
    }

    /** Remove a session key. */
    public void sessionRemove(String key) {
        if (sessionData != null) sessionData.remove(key);
    }

    // Locals

    /** Per-request data shared between middleware and route handlers. */
    public Map<String, Object> locals() { return locals; }

    // Private helpers

    private void ensureFormFields() {
        if (formFields == null) {
            formFields = new HashMap<>();
            String rawBody = body();
            if (!rawBody.isEmpty()) parseUrlEncoded(rawBody, formFields);
        }
    }

    private static void parseUrlEncoded(String body, Map<String, List<String>> out) {
        for (String pair : body.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                try {
                    String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    out.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
                } catch (Exception ignored) {}
            }
        }
    }

    static String extensionToMime(String ext) {
        return switch (ext.toLowerCase()) {
            case "json" -> "application/json";
            case "js", "javascript" -> "application/javascript";
            case "html", "htm" -> "text/html";
            case "text", "txt" -> "text/plain";
            case "css" -> "text/css";
            case "xml" -> "application/xml";
            case "form" -> "application/x-www-form-urlencoded";
            case "multipart" -> "multipart/form-data";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "bin", "octet" -> "application/octet-stream";
            case "urlencoded" -> "application/x-www-form-urlencoded";
            default -> null;
        };
    }

    // RangeResult

    /**
     * Result of parsing an HTTP {@code Range} header via {@code req.range()}.
     *
     * <pre>{@code
     * RangeResult r = req.range(fileSize);
     * if (r == null)                   // no Range header
     * if (r.isInvalid())               // syntactically bad header
     * if (r.isUnsatisfiable())         // range beyond file end
     * for (long[] range : r.ranges())  // [{start,end}, ...]
     * }</pre>
     */
    public static final class RangeResult {

        public static final int INVALID       = -2;
        public static final int UNSATISFIABLE = -1;

        private final String type;
        private final List<long[]> ranges;
        private final int errorCode;

        private RangeResult(String type, List<long[]> ranges, int errorCode) {
            this.type = type;
            this.ranges = ranges;
            this.errorCode = errorCode;
        }

        public static RangeResult invalid() { return new RangeResult(null, null, INVALID); }
        public static RangeResult unsatisfiable() { return new RangeResult(null, null, UNSATISFIABLE); }

        public static RangeResult of(String type, List<long[]> ranges) {
            return new RangeResult(type, Collections.unmodifiableList(ranges), 0);
        }

        public boolean isInvalid() { return errorCode == INVALID; }
        public boolean isUnsatisfiable() { return errorCode == UNSATISFIABLE; }
        public boolean isError() { return errorCode != 0; }
        public int errorCode() { return errorCode; }
        public String  type() { return type; }
        public List<long[]> ranges() { return ranges; }
        public int size() { return ranges != null ? ranges.size() : 0; }
        public long[] get(int index) { return ranges.get(index); }
    }
}
