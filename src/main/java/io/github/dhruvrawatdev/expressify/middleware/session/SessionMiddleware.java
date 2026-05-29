package io.github.dhruvrawatdev.expressify.middleware.session;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.undertow.server.ResponseCommitListener;
import io.undertow.server.handlers.CookieImpl;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory session middleware backed by Caffeine TTL cache.
 * Mirrors express-session behaviour: signed cookie stores the session ID,
 * session data is server-side and expires after {@code maxAge} seconds of inactivity.
 *
 * <pre>{@code
 * app.use(SessionMiddleware.configure(SessionOptions.builder()
 *     .secret("my-secret")
 *     .name("sid")
 *     .maxAge(3600)
 *     .httpOnly(true)
 *     .build()));
 * }</pre>
 */
public class SessionMiddleware implements RouteHandler {

    private final Cache<String, Map<String, Object>> store;
    private final SessionOptions opts;

    public SessionMiddleware(SessionOptions opts) {
        this.opts = opts;
        this.store = Caffeine.newBuilder()
                .expireAfterAccess(Math.max(60, opts.getMaxAge()), TimeUnit.SECONDS)
                .build();
    }

    /**
     * Create a session middleware instance with the given options.
     *
     * <pre>{@code
     * SessionMiddleware session = SessionMiddleware.configure(SessionOptions.builder()
     *     .secret("change-this-in-prod")
     *     .name("sid")
     *     .maxAge(3600)        // 1-hour inactivity TTL
     *     .httpOnly(true)
     *     .sameSite("Lax")
     *     .build());
     * app.use(session);
     *
     * // Read and write session data in a route:
     * app.get("/counter", (req, res) -> {
     *     Map<String, Object> sess = req.session();
     *     int count = ((Number) sess.getOrDefault("count", 0)).intValue() + 1;
     *     sess.put("count", count);
     *     res.json(Map.of("count", count));
     * });
     * }</pre>
     *
     * @param opts session configuration; build with {@link SessionOptions#builder()}
     * @return a {@link SessionMiddleware} instance ready to be passed to {@code app.use()}
     */
    public static SessionMiddleware configure(SessionOptions opts) {
        return new SessionMiddleware(opts);
    }

    @Override
    public void handle(Request req, Response res, NextFunction next) throws Exception {
        String cookieValue = req.cookie(opts.getName());

        String sessionId = null;
        if (cookieValue != null) {
            sessionId = validateAndExtract(cookieValue, opts.getSecret());
        }

        Map<String, Object> data = sessionId != null ? store.getIfPresent(sessionId) : null;
        boolean isNew = (data == null);

        if (isNew) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            data = new ConcurrentHashMap<>();
        }

        req.setSessionData(data, sessionId);

        final String sid = sessionId;
        final Map<String, Object> sessionData = data;

        // For existing sessions or saveUninitialized=true: set cookie eagerly.
        // For new sessions with saveUninitialized=false: defer to just before response is committed
        // so we can check whether the handler actually wrote session data.
        if (!isNew || opts.isSaveUninitialized()) {
            applySessionCookie(req, sid);
        } else {
            // Register a commit listener — fires before headers are written
            req.getExchange().addResponseCommitListener((ResponseCommitListener) exchange -> {
                if (!sessionData.isEmpty()) {
                    store.put(sid, sessionData);
                    applySessionCookieToExchange(exchange, sid);
                }
            });
        }

        next.run();

        // Persist session after handler runs (eager-cookie cases)
        if (isNew && opts.isSaveUninitialized()) {
            store.put(sid, sessionData);
        } else if (!isNew && opts.isResave()) {
            store.put(sid, sessionData);
        }
        // Rolling: refresh TTL on every request for existing sessions
        if (opts.isRolling() && !isNew) {
            store.put(sid, sessionData);
        }
    }

    private void applySessionCookie(Request req, String sessionId) {
        applySessionCookieToExchange(req.getExchange(), sessionId);
    }

    private void applySessionCookieToExchange(io.undertow.server.HttpServerExchange exchange, String sessionId) {
        String signed = sign(sessionId, opts.getSecret());
        CookieImpl cookie = new CookieImpl(opts.getName(), signed);
        cookie.setMaxAge(opts.getMaxAge());
        cookie.setHttpOnly(opts.isHttpOnly());
        cookie.setSecure(opts.isSecure());
        cookie.setPath("/");
        if (opts.getSameSite() != null) cookie.setSameSiteMode(opts.getSameSite());
        exchange.setResponseCookie(cookie);
    }

    /**
     * Explicitly destroy a session by its ID, e.g. on logout.
     *
     * <pre>{@code
     * app.post("/logout", (req, res) -> {
     *     session.destroy(req.getSessionId());
     *     res.clearCookie("sid").redirect("/login");
     * });
     * }</pre>
     *
     * @param sessionId the session ID obtained from {@code req.getSessionId()}
     */
    public void destroy(String sessionId) {
        store.invalidate(sessionId);
    }

    /**
     * Regenerate the session ID — invalidates the old ID and creates a fresh one.
     * Use after privilege elevation (e.g. login) to prevent session-fixation attacks.
     *
     * <pre>{@code
     * app.post("/login", (req, res) -> {
     *     // verify credentials ...
     *     String newSid = session.regenerate(req);
     *     req.session().put("userId", user.getId());
     *     res.json(Map.of("ok", true));
     * });
     * }</pre>
     *
     * @param req the current request whose session ID should be rotated
     * @return the newly generated session ID
     */
    public String regenerate(Request req) {
        String oldId = req.getSessionId();
        if (oldId != null) store.invalidate(oldId);
        String newId = UUID.randomUUID().toString().replace("-", "");
        store.put(newId, new ConcurrentHashMap<>());
        return newId;
    }

    // HMAC helpers

    static String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return value + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Session signing failed", e);
        }
    }

    static String validateAndExtract(String signedValue, String secret) {
        int dot = signedValue.lastIndexOf('.');
        if (dot < 0) return null;
        String value    = signedValue.substring(0, dot);
        String expected = sign(value, secret);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signedValue.getBytes(StandardCharsets.UTF_8))) return null;
        return value;
    }
}
