package io.github.dhruvrawatdev.expressify.middleware.cookie_session;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

/**
 * Stateless cookie-based session middleware — port of the Node.js {@code cookie-session} package.
 *
 * <p>Unlike {@code SessionMiddleware} (server-side memory store), this middleware keeps all
 * session data in the client's cookie, encoded as Base64 JSON. No server-side storage is needed.
 * With {@code signed = true} (default), the cookie value is HMAC-SHA256 signed so clients cannot
 * tamper with it.
 *
 * <pre>{@code
 * // Simple setup with a secret
 * app.use(CookieSession.create("my-secret"));
 *
 * // Full options
 * app.use(CookieSession.create(CookieSessionOptions.builder()
 *     .name("sess")
 *     .secret("my-secret")
 *     .maxAge(24 * 60 * 60)   // 1 day
 *     .httpOnly(true)
 *     .sameSite("Lax")
 *     .build()));
 *
 * // Reading and writing the session in a route
 * app.get("/", (req, res) -> {
 *     Map<String, Object> sess = CookieSession.session(req);
 *     sess.put("views", ((Number) sess.getOrDefault("views", 0)).intValue() + 1);
 *     res.json(sess);
 * });
 *
 * // Destroying the session
 * app.post("/logout", (req, res) -> {
 *     CookieSession.destroy(req);
 *     res.redirect("/");
 * });
 * }</pre>
 */
public final class CookieSession {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SESS_KEY = "__cookieSession";
    private static final String DEST_KEY = "__cookieSessionDestroyed";

    private CookieSession() {}

    // Factory methods

    /**
     * Cookie session with a single HMAC-SHA256 signing secret and sensible defaults.
     *
     * <p>Session name defaults to {@code "session"}, session cookie (no max-age),
     * {@code HttpOnly=true}, signed with HMAC-SHA256.
     *
     * <pre>{@code
     * app.use(CookieSession.create("my-super-secret-key"));
     * }</pre>
     *
     * @param secret HMAC-SHA256 key used to sign the cookie value
     * @return a {@link RouteHandler} that loads, exposes, and persists the cookie session
     */
    public static RouteHandler create(String secret) {
        return create(CookieSessionOptions.builder().secret(secret).build());
    }

    /**
     * Cookie session with full configuration options.
     *
     * @param opts cookie-session configuration; build with {@link CookieSessionOptions#builder()}
     * @return a {@link RouteHandler} that loads, exposes, and auto-persists the cookie session
     * @throws IllegalArgumentException if {@code signed = true} but no signing keys are provided
     */
    public static RouteHandler create(CookieSessionOptions opts) {
        if (opts.signed() && opts.keys().isEmpty()) {
            throw new IllegalArgumentException("CookieSession: at least one signing key is required when signed=true");
        }
        return (req, res, next) -> {
            // ── Load session from cookie ──
            Map<String, Object> session = loadSession(req, opts);
            req.locals().put(SESS_KEY, session);
            req.locals().put(DEST_KEY, false);

            // ── Save session to cookie before response is sent ──
            res.onPreSend(() -> persistSession(req, res, opts));

            next.run();
        };
    }

    // Public session accessors

    /**
     * Return the mutable session map for the current request.
     *
     * <p>Changes to the returned map are automatically serialised into the response cookie
     * when the response is committed — no explicit save call is needed.
     *
     * <pre>{@code
     * app.get("/cart", (req, res) -> {
     *     Map<String, Object> sess = CookieSession.session(req);
     *     List<String> items = (List<String>) sess.computeIfAbsent("items", k -> new ArrayList<>());
     *     items.add("Widget");
     *     res.json(sess);
     * });
     * }</pre>
     *
     * @param req the current request; must have {@code CookieSession} middleware upstream
     * @return the live session map, never {@code null}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> session(Request req) {
        Object s = req.locals().get(SESS_KEY);
        if (s instanceof Map<?, ?> m) return (Map<String, Object>) m;
        Map<String, Object> empty = new LinkedHashMap<>();
        req.locals().put(SESS_KEY, empty);
        return empty;
    }

    /**
     * Destroy the session — clears its data and instructs the middleware to expire the cookie
     * in the response (sets {@code Max-Age=0}).
     *
     * <pre>{@code
     * app.post("/logout", (req, res) -> {
     *     CookieSession.destroy(req);
     *     res.redirect("/");
     * });
     * }</pre>
     *
     * @param req the current request whose cookie session should be cleared
     */
    public static void destroy(Request req) {
        req.locals().put(DEST_KEY, true);
        req.locals().put(SESS_KEY, new LinkedHashMap<>());
    }

    // Private helpers

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadSession(Request req, CookieSessionOptions opts) {
        String raw = req.cookies().get(opts.name());
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            String payload;
            if (opts.signed() && raw.startsWith("s:")) {
                payload = unsign(raw.substring(2), opts.keys());
                if (payload == null) return new LinkedHashMap<>();
            } else {
                payload = raw;
            }
            byte[] json = Base64.getDecoder().decode(payload);
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>(){});
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static void persistSession(Request req, Response res, CookieSessionOptions opts) {
        boolean destroyed = Boolean.TRUE.equals(req.locals().get(DEST_KEY));

        io.undertow.server.handlers.CookieImpl c =
                new io.undertow.server.handlers.CookieImpl(opts.name());
        c.setPath(opts.path() != null ? opts.path() : "/");
        c.setHttpOnly(opts.httpOnly());
        c.setSecure(opts.secure());
        if (opts.domain() != null) c.setDomain(opts.domain());
        if (opts.sameSite() != null) c.setSameSiteMode(opts.sameSite());

        if (destroyed) {
            c.setValue("");
            c.setMaxAge(0);
        } else {
            Map<String, Object> sess = session(req);
            if (sess.isEmpty()) return; // nothing to save

            String encoded;
            try {
                byte[] json = OBJECT_MAPPER.writeValueAsBytes(sess);
                encoded = Base64.getEncoder().withoutPadding().encodeToString(json);
            } catch (Exception e) {
                return;
            }

            String cookieValue = opts.signed() && !opts.keys().isEmpty()
                    ? "s:" + sign(encoded, opts.keys().get(0))
                    : encoded;

            c.setValue(cookieValue);
            if (opts.maxAge() >= 0) c.setMaxAge(opts.maxAge());
        }

        res.applyResponseCookie(c);
    }

    /** HMAC-SHA256 sign: {@code base64Payload.base64mac} */
    private static String sign(String value, String secret) {
        return Response.hmacSign(value, secret);
    }

    /**
     * Verify a signed value ({@code value.mac}) against multiple secrets.
     * Returns the plain value on success, or {@code null} on failure.
     */
    private static String unsign(String signedValue, List<String> secrets) {
        int dot = signedValue.lastIndexOf('.');
        if (dot < 0) return null;
        String value    = signedValue.substring(0, dot);
        String givenMac = signedValue.substring(dot + 1);

        for (String secret : secrets) {
            try {
                String full  = Response.hmacSign(value, secret);
                int    fdot = full.lastIndexOf('.');
                if (fdot < 0) continue;
                String expected = full.substring(fdot + 1);
                if (constantTimeEquals(givenMac, expected)) return value;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
