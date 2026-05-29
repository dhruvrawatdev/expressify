package io.github.dhruvrawatdev.expressify.http;

import io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieOptions;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.github.dhruvrawatdev.expressify.internal.HttpHeaders;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Express.js-compatible response wrapper around Undertow's {@link HttpServerExchange}.
 *
 * <p>Mirrors the Express.js {@code res} API: status, send, json, jsonp, redirect,
 * cookies, headers, file serving, template rendering, format negotiation, and more.
 */
public class Response {

    /** Reusable BAOS that exposes its internal buffer — avoids toByteArray() copy on the hot path. */
    private static final class FastBAOS extends java.io.ByteArrayOutputStream {
        FastBAOS(int capacity) { super(capacity); }
        byte[] rawBuf() { return buf; }
        int    rawLen() { return count; }
        java.nio.ByteBuffer asByteBuffer() { return java.nio.ByteBuffer.wrap(buf, 0, count); }
        @Override public String toString(java.nio.charset.Charset cs) { return new String(buf, 0, count, cs); }
    }

    private static final ThreadLocal<FastBAOS> JSON_BUFFER =
            ThreadLocal.withInitial(() -> new FastBAOS(512));

    private static final Map<Integer, String> STATUS_TEXT = new HashMap<>();
    static {
        STATUS_TEXT.put(100, "Continue"); STATUS_TEXT.put(101, "Switching Protocols");
        STATUS_TEXT.put(200, "OK"); STATUS_TEXT.put(201, "Created");
        STATUS_TEXT.put(202, "Accepted"); STATUS_TEXT.put(204, "No Content");
        STATUS_TEXT.put(206, "Partial Content"); STATUS_TEXT.put(301, "Moved Permanently");
        STATUS_TEXT.put(302, "Found");  STATUS_TEXT.put(304, "Not Modified");
        STATUS_TEXT.put(307, "Temporary Redirect"); STATUS_TEXT.put(308, "Permanent Redirect");
        STATUS_TEXT.put(400, "Bad Request");  STATUS_TEXT.put(401, "Unauthorized");
        STATUS_TEXT.put(403, "Forbidden"); STATUS_TEXT.put(404, "Not Found");
        STATUS_TEXT.put(405, "Method Not Allowed");  STATUS_TEXT.put(406, "Not Acceptable");
        STATUS_TEXT.put(408, "Request Timeout"); STATUS_TEXT.put(409, "Conflict");
        STATUS_TEXT.put(410, "Gone"); STATUS_TEXT.put(413, "Payload Too Large");
        STATUS_TEXT.put(414, "URI Too Long"); STATUS_TEXT.put(415, "Unsupported Media Type");
        STATUS_TEXT.put(422, "Unprocessable Entity"); STATUS_TEXT.put(429, "Too Many Requests");
        STATUS_TEXT.put(500, "Internal Server Error");STATUS_TEXT.put(501, "Not Implemented");
        STATUS_TEXT.put(502, "Bad Gateway"); STATUS_TEXT.put(503, "Service Unavailable");
        STATUS_TEXT.put(504, "Gateway Timeout");
    }

    private HttpServerExchange exchange;
    private Map<String, Object> settings;
    private Map<String, TemplateEngine> engines;

    private Request req;

    private boolean committed  = false;
    private int statusCode = 200;

    private final Map<String, Object> locals = new HashMap<>();

    private final List<Runnable> preSendHooks = new ArrayList<>();

    private boolean bufferingEnabled  = false;
    private byte[] bufferedBody;
    private String bufferedContentType;

    public Response(HttpServerExchange exchange,
                    Map<String, Object> settings,
                    Map<String, TemplateEngine> engines) {
        this.exchange = exchange;
        this.settings = settings;
        this.engines = engines;
    }

    /** Set the paired Request reference (called by UndertowAdapter). */
    public void setRequest(Request req) { this.req = req; }

    /** Framework-internal: reuse this instance for a new request on the same worker thread. */
    public void reset(HttpServerExchange exchange, Map<String, Object> settings,
                      Map<String, TemplateEngine> engines) {
        this.exchange = exchange;
        this.settings = settings;
        this.engines = engines;
        this.req = null;
        this.committed = false;
        this.statusCode = 200;
        this.locals.clear();
        this.preSendHooks.clear();
        this.bufferingEnabled = false;
        this.bufferedBody = null;
        this.bufferedContentType = null;
    }

    /** Set a cookie directly on the underlying exchange (used by cookie-session middleware). */
    public void applyResponseCookie(io.undertow.server.handlers.CookieImpl cookie) {
        exchange.setResponseCookie(cookie);
    }

    /**
     * Register a hook that is called just before the response body is sent.
     * Useful for middleware that needs to set headers after the route handler runs
     * but before bytes hit the wire.
     */
    public void onPreSend(Runnable hook) {
        preSendHooks.add(hook);
    }

    private void firePreSend() {
        for (Runnable hook : preSendHooks) {
            try { hook.run(); } catch (Throwable ignored) {}
        }
        preSendHooks.clear();
    }

    /** Set the HTTP status code. Returns {@code this} for chaining. */
    public Response status(int code) {
        this.statusCode = code;
        exchange.setStatusCode(code);
        return this;
    }

    /** Get the current HTTP status code. */
    public int getStatus() { return statusCode; }

    /**
     * Send a string response body. Content-Type defaults to {@code text/html; charset=utf-8}
     * unless already set. Handles HEAD requests (no body, correct Content-Length).
     */
    public void send(String body) {
        if (committed) return;

        // Append charset to text/* types that omit it
        String ct = exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
        if (ct == null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
        } else if (ct.startsWith("text/") && !ct.contains("charset")) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ct + "; charset=utf-8");
        }

        byte[] bytes = (body != null ? body : "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sendRawBytes(bytes, bytes.length, null); // Content-Type already set above
    }

    /** Send any object: strings as text/html, byte arrays as binary, everything else as JSON. */
    public void send(Object body) {
        if (body == null) {
            send("");
        } else if (body instanceof String s) {
            send(s);
        } else if (body instanceof byte[] b) {
            send(b);
        } else {
            json(body);
        }
    }

    private static String computeETag(byte[] bytes) {
        return computeETag(bytes, bytes.length);
    }

    private static String computeETag(byte[] buf, int len) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(buf, 0, len);
        return "W/\"" + len + "-" + Long.toHexString(crc.getValue()) + "\"";
    }

    /**
     * Core send implementation shared by json(), send(String), and send(byte[]).
     * {@code buf[0..len)} is the body to send. {@code defaultCt} is applied only if no
     * Content-Type header has already been set by the caller.
     */
    private void sendRawBytes(byte[] buf, int len, String defaultCt) {
        committed = true;
        firePreSend();

        String ct = exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
        if (ct == null && defaultCt != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, defaultCt);
        }

        boolean etagEnabled = !Boolean.FALSE.equals(settings.get("etag"));
        if (etagEnabled && len > 0 && exchange.getResponseHeaders().getFirst(HttpHeaders.ETAG) == null) {
            exchange.getResponseHeaders().put(HttpHeaders.ETAG, computeETag(buf, len));
        }

        if (req != null && req.fresh()) statusCode = 304;
        exchange.setStatusCode(statusCode);

        if (statusCode == 204 || statusCode == 304) {
            exchange.getResponseHeaders().remove(Headers.CONTENT_TYPE);
            exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
            exchange.getResponseHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
            exchange.endExchange();
            return;
        }
        if (statusCode == 205) {
            exchange.getResponseHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, "0");
            exchange.endExchange();
            return;
        }

        if (bufferingEnabled) {
            bufferedContentType = ct != null ? ct : defaultCt;
            bufferedBody = (len == buf.length) ? buf : java.util.Arrays.copyOf(buf, len);
            return;
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(len));
        if (req != null && "HEAD".equals(req.method())) {
            exchange.endExchange();
            return;
        }
        exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(buf, 0, len));
    }

    /** Send a binary body. Content-Type defaults to {@code application/octet-stream} unless set. */
    public void send(byte[] body) {
        if (committed) return;
        byte[] safe = body != null ? body : new byte[0];
        sendRawBytes(safe, safe.length, "application/octet-stream");
    }

    /**
     * Send a JSON-serialized response. Sets Content-Type to {@code application/json}.
     * Passing {@code null} sends the JSON literal {@code null}.
     *
     * <p>Hot path: ThreadLocal {@link FastBAOS} is reused per worker thread, avoiding
     * per-request allocation. Bytes are sent directly via a zero-copy {@link java.nio.ByteBuffer}
     * wrapping the BAOS internal buffer — no intermediate {@code String} conversion occurs
     * unless {@code json spaces} or {@code json escape} settings are active.
     */
    public void json(Object data) {
        if (committed) return;

        // String from req.body() is already serialized JSON — pass through directly
        if (data instanceof String s) {
            if (getHeader("Content-Type") == null)
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            sendRawBytes(bytes, bytes.length, null);
            return;
        }

        FastBAOS baos = JSON_BUFFER.get();
        baos.reset();
        try {
            byte[] bytes = jsonStringify(data).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            baos.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }

        if (getHeader("Content-Type") == null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        }

        Object spacesObj = settings.get("json spaces");
        boolean needsPretty = spacesObj instanceof Number n && n.intValue() > 0;
        boolean needsEscape = Boolean.TRUE.equals(settings.get("json escape"));

        if (needsPretty || needsEscape) {
            // Formatting requires a String round-trip; allocate only in this branch
            String str = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (needsPretty) str = prettyPrint(str, ((Number) spacesObj).intValue());
            if (needsEscape) str = escapeJsonHtml(str);
            byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            sendRawBytes(bytes, bytes.length, null);
        } else {
            // Zero-copy: wrap ThreadLocal buffer directly — safe because Undertow
            // operates in blocking mode (exchange.startBlocking() was called) so
            // send() completes before we return and reset the buffer.
            sendRawBytes(baos.rawBuf(), baos.rawLen(), null);
        }
    }

    /**
     * Send a JSONP response. Wraps JSON in a callback function call when the request
     * has a callback query parameter (default: "callback"). Falls back to plain JSON.
     */
    public void jsonp(Object data) {
        if (committed) return;
        String callbackName = (String) settings.getOrDefault("jsonp callback name", "callback");
        String callbackFn   = req != null ? req.query(callbackName) : null;

        String json;
        try {
            json = jsonStringify(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON for JSONP", e);
        }

        if (callbackFn != null && !callbackFn.isEmpty()) {
            callbackFn = callbackFn.replaceAll("[^\\[\\]\\w$.]", "");
            header("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    "text/javascript; charset=utf-8");
            String body = "/**/ typeof " + callbackFn + " === 'function' && "
                         + callbackFn + "(" + json + ");";
            send(body);
        } else {
            header("X-Content-Type-Options", "nosniff");
            json(data);
        }
    }

    /**
     * Send a response using the given HTTP status code and its standard text description.
     * E.g. {@code res.sendStatus(404)} → "Not Found" with Content-Type text/plain.
     */
    public void sendStatus(int code) {
        String text = STATUS_TEXT.getOrDefault(code, String.valueOf(code));
        status(code).type("text/plain; charset=utf-8").send(text);
    }

    /** End the response with no body. */
    public void end() {
        if (committed) return;
        committed = true;
        firePreSend();
        exchange.setStatusCode(statusCode);
        exchange.endExchange();
    }


    /** Redirect (302 by default). */
    public void redirect(String url) { redirect(302, url); }

    /** Redirect with explicit status code. */
    public void redirect(int status, String url) {
        if (committed) return;
        location(url);
        String loc = getHeader("Location");
        if (loc == null) loc = url;

        String text = STATUS_TEXT.getOrDefault(status, "Redirecting");
        String body = "<!DOCTYPE html><html><head><title>" + text + "</title></head>"
                    + "<body><p>" + text + ". Redirecting to <a href=\"" + escapeHtml(loc)
                    + "\">" + escapeHtml(loc) + "</a></p></body></html>";

        committed = true;
        firePreSend();
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(bytes.length));
        if (req != null && "HEAD".equals(req.method())) {
            exchange.endExchange();
        } else {
            exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(bytes));
        }
    }

    // Headers

    /**
     * Set a response header. Returns {@code this} for chaining.
     * Alias: {@link #set(String, String)}.
     */
    public Response header(String name, String value) {
        exchange.getResponseHeaders().put(HttpString.tryFromString(name), value);
        return this;
    }

    /** Alias for {@link #header(String, String)} — Express {@code res.set()} parity. */
    public Response set(String name, String value) { return header(name, value); }

    /** Set multiple headers at once from a map. */
    public Response set(Map<String, String> headers) {
        headers.forEach(this::header);
        return this;
    }

    /** Get a response header already set. Returns null if absent. */
    public String get(String name) { return getHeader(name); }

    /** Get a response header already set. Returns null if absent. */
    public String getHeader(String name) {
        return exchange.getResponseHeaders().getFirst(HttpString.tryFromString(name));
    }

    /** Remove a response header. Returns {@code this} for chaining. */
    public Response removeHeader(String name) {
        exchange.getResponseHeaders().remove(HttpString.tryFromString(name));
        return this;
    }

    /**
     * Append a value to an existing header (comma-separated).
     * Creates the header if it does not exist.
     */
    public Response append(String name, String value) {
        String existing = getHeader(name);
        return header(name, existing != null ? existing + ", " + value : value);
    }

    /**
     * Set the Content-Type. Accepts full MIME types or short extensions:
     * "json" → "application/json; charset=utf-8", "html" → "text/html; charset=utf-8", etc.
     * Returns {@code this} for chaining.
     */
    public Response type(String type) {
        if (type != null && !type.contains("/")) {
            String resolved = mimeFromExtension(type);
            type = resolved != null ? resolved : "application/octet-stream";
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, type);
        return this;
    }

    /** Alias for {@link #type(String)}. */
    public Response contentType(String type) { return type(type); }

    /**
     * Append {@code field} to the Vary response header (accumulating, not replacing).
     * Returns {@code this} for chaining.
     */
    public Response vary(String field) {
        String current = getHeader("Vary");
        if (current == null || current.isBlank()) {
            return header("Vary", field);
        }
        for (String existing : current.split(",")) {
            if (existing.trim().equalsIgnoreCase(field)) return this;
        }
        return header("Vary", current + ", " + field);
    }

    /**
     * Set the Link header from a map of rel → URL entries.
     * Existing Link values are preserved and new ones are appended.
     */
    public Response links(Map<String, Object> linksMap) {
        String existing = getHeader("Link");
        StringBuilder sb = new StringBuilder(existing != null ? existing : "");
        boolean first = (existing == null || existing.isEmpty());
        for (Map.Entry<String, Object> e : linksMap.entrySet()) {
            String rel = e.getKey();
            Object val = e.getValue();
            List<String> urls = new ArrayList<>();
            if (val instanceof List<?> list) {
                for (Object item : list) urls.add(item.toString());
            } else {
                urls.add(val.toString());
            }
            for (String url : urls) {
                if (!first) sb.append(", ");
                sb.append("<").append(url).append(">; rel=\"").append(rel).append("\"");
                first = false;
            }
        }
        return header("Link", sb.toString());
    }

    /**
     * Set the Location header. Returns {@code this} for chaining.
     * Pass {@code "back"} to redirect to the Referer, falling back to {@code "/"}.
     */
    public Response location(String url) {
        if ("back".equals(url)) {
            String ref = req != null ? req.get("Referer") : null;
            url = (ref != null && !ref.isEmpty()) ? ref : "/";
        }
        return header("Location", encodeUrl(url));
    }

    // Cookies

    /** Set a cookie with default options. */
    public Response cookie(String name, String value) {
        setCookie(name, value, null);
        return this;
    }

    /** Set a cookie with explicit options. */
    public Response cookie(String name, String value, CookieOptions opts) {
        setCookie(name, value, opts);
        return this;
    }

    /** Set a cookie whose non-String value is serialized as {@code j:JSON} (mirrors Express behaviour). */
    public Response cookie(String name, Object value) {
        return cookie(name, serializeCookieValue(value));
    }

    /** Set a cookie whose non-String value is serialized as {@code j:JSON}, with options. */
    public Response cookie(String name, Object value, CookieOptions opts) {
        return cookie(name, serializeCookieValue(value), opts);
    }

    /** Expire a cookie immediately (set Max-Age=0). */
    public Response clearCookie(String name) {
        exchange.getResponseHeaders().add(Headers.SET_COOKIE, name + "=; Path=/; Max-Age=0");
        return this;
    }

    /** Expire a cookie with specific path/domain options. */
    public Response clearCookie(String name, CookieOptions opts) {
        String path = opts != null && opts.getPath() != null ? opts.getPath() : "/";
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=; Path=").append(path).append("; Max-Age=0");
        if (opts != null && opts.getDomain() != null) sb.append("; Domain=").append(opts.getDomain());
        exchange.getResponseHeaders().add(Headers.SET_COOKIE, sb.toString());
        return this;
    }

    // Content Negotiation

    /**
     * Set Content-Disposition to attachment (for file download dialogs).
     * If {@code filename} is provided, also sets the Content-Type from its extension.
     */
    public Response attachment(String filename) {
        if (filename != null && !filename.isEmpty()) {
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
            if (!ext.isEmpty()) type(ext);
            header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        } else {
            header("Content-Disposition", "attachment");
        }
        return this;
    }

    /**
     * Respond based on the request's Accept header.
     * The {@code callbacks} map maps MIME types (or short extensions) to {@link Runnable}s.
     * A "default" key serves as fallback. Sends 406 if no match and no default.
     */
    public void format(Map<String, Runnable> callbacks) {
        String accept = req != null ? req.get("Accept") : null;
        List<String> keys = new ArrayList<>();
        for (String k : callbacks.keySet()) {
            if (!"default".equals(k)) keys.add(k);
        }
        vary("Accept");
        String matched = null;
        for (String key : keys) {
            String mime = key.contains("/") ? key : mimeFromExtension(key);
            if (mime == null) mime = key;
            if (accept == null || accept.isBlank() || accept.contains("*/*")
                    || accept.contains(mime) || accept.contains(key)) {
                matched = key;
                break;
            }
        }
        if (matched != null) {
            String mime = matched.contains("/") ? matched : mimeFromExtension(matched);
            if (mime != null) type(mime);
            callbacks.get(matched).run();
        } else if (callbacks.containsKey("default")) {
            callbacks.get("default").run();
        } else {
            status(406).send("Not Acceptable");
        }
    }

    // File Serving

    /** Send a file as the response body with automatic MIME detection. */
    public void sendFile(String filePath) {
        if (committed) return;
        committed = true;
        firePreSend();

        Path p = Path.of(filePath);
        if (!Files.exists(p)) {
            exchange.setStatusCode(404);
            exchange.getResponseSender().send("File not found: " + filePath);
            return;
        }
        try {
            long fileSize = Files.size(p);
            String mime   = guessMimeType(filePath);
            exchange.setStatusCode(statusCode);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, mime);
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(fileSize));
            if (req != null && "HEAD".equals(req.method())) {
                exchange.endExchange();
                return;
            }
            try (InputStream in = Files.newInputStream(p)) {
                OutputStream out = exchange.getOutputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to send file: " + filePath, e);
        }
    }

    /** Trigger a browser download dialog for the file. */
    public void download(String filePath) {
        Path p = Path.of(filePath);
        attachment(p.getFileName().toString());
        sendFile(filePath);
    }

    /** Trigger a browser download dialog with a custom filename. */
    public void download(String filePath, String filename) {
        attachment(filename);
        sendFile(filePath);
    }

    // Template Rendering

    /** Render a template using the configured default engine. */
    public void render(String templateName) {
        render(templateName, Map.of(), null);
    }

    /** Render a template with a model. */
    public void render(String templateName, Map<String, Object> model) {
        render(templateName, model, null);
    }

    /** Render with an explicit engine override. */
    public void render(String templateName, Map<String, Object> model, String engineName) {
        if (committed) return;
        String resolvedEngine = engineName != null ? engineName
                : (String) settings.getOrDefault("view engine", "thymeleaf");
        TemplateEngine engine = engines.get(resolvedEngine);
        if (engine == null) {
            committed = true;
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("No template engine configured for: " + resolvedEngine);
            return;
        }
        String viewsDir = (String) settings.getOrDefault("views", "src/main/resources/templates");
        Map<String, Object> merged = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> appLocals = (Map<String, Object>) settings.getOrDefault("__locals", Map.of());
        merged.putAll(appLocals);
        merged.putAll(locals);
        merged.putAll(model);

        String html;
        try {
            html = engine.render(viewsDir, templateName, merged);
        } catch (Exception e) {
            throw new RuntimeException("Template rendering failed for: " + templateName, e);
        }
        if (getHeader("Content-Type") == null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html; charset=utf-8");
        }
        send(html);
    }

    // Streaming

    /** Write a chunk of bytes directly to the response (progressive streaming). */
    public void write(byte[] chunk) throws IOException {
        if (!committed) {
            committed = true;
            firePreSend();
            exchange.setStatusCode(statusCode);
        }
        exchange.getOutputStream().write(chunk);
        exchange.getOutputStream().flush();
    }

    /** Pipe an InputStream to the response body without loading it fully in memory. */
    public void pipe(InputStream stream) throws IOException {
        if (!committed) {
            committed = true;
            firePreSend();
            exchange.setStatusCode(statusCode);
        }
        OutputStream out = exchange.getOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
        out.flush();
    }

    // (Compression middleware)

    public void enableBuffering() { this.bufferingEnabled = true; }
    public boolean isBufferingEnabled() { return bufferingEnabled; }
    public byte[]  getBufferedBody() { return bufferedBody; }
    public String  getBufferedContentType() { return bufferedContentType; }

    public void flushBuffered(byte[] body, String contentEncoding) {
        firePreSend();
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                bufferedContentType != null ? bufferedContentType : "application/octet-stream");
        if (contentEncoding != null) {
            exchange.getResponseHeaders().put(HttpHeaders.CONTENT_ENCODING, contentEncoding);
            exchange.getResponseHeaders().put(HttpString.tryFromString("Vary"), "Accept-Encoding");
        }
        exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(body.length));
        if (req != null && "HEAD".equals(req.method())) {
            exchange.endExchange();
            return;
        }
        exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(body));
    }

    // State

    public boolean isCommitted()         { return committed; }
    public Map<String, Object> locals()  { return locals; }

    // HMAC cookie signing (public for middleware use)

    public static String hmacSign(String value, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return value + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Cookie signing failed", e);
        }
    }

    // Private helpers

    private void setCookie(String name, String value, CookieOptions opts) {
        String cookieValue = value;
        if (opts != null && opts.isSigned()) {
            String sigSecret = opts.getSecret() != null ? opts.getSecret()
                    : (req != null ? req.secret() : null);
            if (sigSecret != null) {
                cookieValue = "s:" + hmacSign(value, sigSecret);
            }
        }
        CookieImpl c = new CookieImpl(name, cookieValue);
        if (opts != null) {
            if (opts.getMaxAge() >= 0) c.setMaxAge(opts.getMaxAge());
            c.setHttpOnly(opts.isHttpOnly());
            c.setSecure(opts.isSecure());
            c.setPath(opts.getPath() != null ? opts.getPath() : "/");
            if (opts.getDomain() != null) c.setDomain(opts.getDomain());
            if (opts.getSameSite() != null) c.setSameSiteMode(opts.getSameSite());
            if (opts.getExpires() != null) {
                try {
                    java.util.Date d = java.util.Date.from(
                        java.time.ZonedDateTime.parse(opts.getExpires(),
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                    c.setExpires(d);
                } catch (Exception ignored) {}
            }
        } else {
            c.setPath("/");
        }
        exchange.setResponseCookie(c);
    }

    private static String serializeCookieValue(Object value) {
        if (value instanceof String s) return s;
        String raw = "j:" + jsonStringify(value);
        // Percent-encode all chars that aren't unreserved — avoids invalid cookie value chars
        // (mirrors Express.js encodeURIComponent behavior for j: prefixed values)
        StringBuilder sb = new StringBuilder(raw.length() * 2);
        for (char c : raw.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else {
                sb.append(String.format("%%%02X", (int) c));
            }
        }
        return sb.toString();
    }

    private static String jsonStringify(Object value) {
        StringBuilder sb = new StringBuilder(64);
        appendJsonValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null)            { sb.append("null"); }
        else if (value instanceof Boolean) { sb.append(value); }
        else if (value instanceof Number)  { sb.append(value); }
        else if (value instanceof String s){ appendJsonString(sb, s); }
        else if (value instanceof java.util.List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) sb.append(','); appendJsonValue(sb, list.get(i)); }
            sb.append(']');
        } else if (value instanceof java.util.Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                appendJsonString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                appendJsonValue(sb, e.getValue());
            }
            sb.append('}');
        } else {
            appendJsonString(sb, value.toString());
        }
    }

    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    private static String encodeUrl(String url) {
        try {
            new java.net.URI(url);
            return url; // already a valid URI
        } catch (java.net.URISyntaxException ignored) {}
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < url.length()) {
            char c = url.charAt(i);
            // Preserve existing %-encoded sequences to avoid double-encoding
            if (c == '%' && i + 2 < url.length()
                    && isHexChar(url.charAt(i + 1)) && isHexChar(url.charAt(i + 2))) {
                sb.append(c).append(url.charAt(i + 1)).append(url.charAt(i + 2));
                i += 3;
            } else {
                byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    int v = b & 0xff;
                    if (isAllowedInUrl(v)) sb.append((char) v);
                    else                   sb.append(String.format("%%%02X", v));
                }
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    private static boolean isAllowedInUrl(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
            || c == '-' || c == '.' || c == '_' || c == '~' || c == ':' || c == '/'
            || c == '?' || c == '#' || c == '[' || c == ']' || c == '@' || c == '!'
            || c == '$' || c == '&' || c == '\'' || c == '(' || c == ')' || c == '*'
            || c == '+' || c == ',' || c == ';' || c == '=';
    }

    private static String prettyPrint(String compact, int spaces) {
        String pad = " ".repeat(Math.max(1, spaces));
        StringBuilder out = new StringBuilder(compact.length() * 2);
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (esc)            { out.append(c); esc = false; continue; }
            if (inStr) {
                if (c == '\\')  { out.append(c); esc = true; }
                else if (c == '"') { out.append(c); inStr = false; }
                else             out.append(c);
                continue;
            }
            switch (c) {
                case '"' -> { inStr = true; out.append(c); }
                case '{', '[' -> {
                    out.append(c);
                    int peek = i + 1;
                    while (peek < compact.length() && compact.charAt(peek) == ' ') peek++;
                    char nc = peek < compact.length() ? compact.charAt(peek) : 0;
                    boolean empty = (c == '{' && nc == '}') || (c == '[' && nc == ']');
                    if (!empty) {
                        depth++;
                        out.append('\n');
                        for (int d = 0; d < depth; d++) out.append(pad);
                    }
                }
                case '}', ']' -> {
                    int len = out.length();
                    char prev = len > 0 ? out.charAt(len - 1) : 0;
                    boolean empty = (prev == '{' || prev == '[');
                    if (!empty) {
                        depth--;
                        out.append('\n');
                        for (int d = 0; d < depth; d++) out.append(pad);
                    }
                    out.append(c);
                }
                case ',' -> {
                    out.append(c).append('\n');
                    for (int d = 0; d < depth; d++) out.append(pad);
                }
                case ':' -> out.append(": ");
                default  -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String escapeJsonHtml(String json) {
        return json.replace("<",  "\\u003c")
                   .replace(">",  "\\u003e")
                   .replace("&",  "\\u0026")
                   .replace(" ", "\\u2028")
                   .replace(" ", "\\u2029");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String mimeFromExtension(String ext) {
        return switch (ext.toLowerCase()) {
            case "json" -> "application/json; charset=utf-8";
            case "js","javascript" -> "application/javascript; charset=utf-8";
            case "html","htm" -> "text/html; charset=utf-8";
            case "text","txt","plain" -> "text/plain; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "xml" -> "application/xml; charset=utf-8";
            case "png" -> "image/png";
            case "jpg","jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "pdf" -> "application/pdf";
            case "bin","octet" -> "application/octet-stream";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            case "ttf" -> "font/ttf";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "mp3" -> "audio/mpeg";
            case "zip" -> "application/zip";
            case "gz" -> "application/gzip";
            case "tar" -> "application/x-tar";
            default -> null;
        };
    }

    private static String guessMimeType(String filePath) {
        String lower = filePath.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot >= 0) {
            String mime = mimeFromExtension(lower.substring(dot + 1));
            if (mime != null) return mime;
        }
        String probed = URLConnection.guessContentTypeFromName(filePath);
        return probed != null ? probed : "application/octet-stream";
    }
}
