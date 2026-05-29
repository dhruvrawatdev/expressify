package io.github.dhruvrawatdev.expressify;

import io.github.dhruvrawatdev.expressify.router.core.Router;
import io.github.dhruvrawatdev.expressify.router.core.RouteBuilder;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncErrorHandler;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncRouteHandler;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncSimpleHandler;
import io.github.dhruvrawatdev.expressify.router.handler.ErrorHandler;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.ParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.router.handler.SimpleHandler;
import io.github.dhruvrawatdev.expressify.router.registry.RouteRegistry;
import io.github.dhruvrawatdev.expressify.internal.UndertowAdapter;
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.*;
import io.github.dhruvrawatdev.expressify.middleware.compression.Compression;
import io.github.dhruvrawatdev.expressify.middleware.compression.CompressionOptions;
import io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieParser;
import io.github.dhruvrawatdev.expressify.middleware.cors.Cors;
import io.github.dhruvrawatdev.expressify.middleware.cors.CorsOptions;
import io.github.dhruvrawatdev.expressify.middleware.helmet.Helmet;
import io.github.dhruvrawatdev.expressify.middleware.helmet.HelmetOptions;
import io.github.dhruvrawatdev.expressify.middleware.method_override.MethodOverride;
import io.github.dhruvrawatdev.expressify.middleware.method_override.MethodOverrideOptions;
import io.github.dhruvrawatdev.expressify.middleware.morgan.Morgan;
import io.github.dhruvrawatdev.expressify.middleware.morgan.MorganOptions;
import io.github.dhruvrawatdev.expressify.middleware.cookie_session.CookieSession;
import io.github.dhruvrawatdev.expressify.middleware.cookie_session.CookieSessionOptions;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimiter;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimiterOptions;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.ParsedRateLimit;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.RateLimitHeaderParser;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.RateLimitHeaderParserOptions;
import io.github.dhruvrawatdev.expressify.middleware.slow_down.SlowDown;
import io.github.dhruvrawatdev.expressify.middleware.slow_down.SlowDownOptions;
import io.github.dhruvrawatdev.expressify.middleware.response_time.ResponseTime;
import io.github.dhruvrawatdev.expressify.middleware.response_time.ResponseTimeOptions;
import io.github.dhruvrawatdev.expressify.middleware.serve_static.ServeStatic;
import io.github.dhruvrawatdev.expressify.middleware.serve_static.StaticOptions;
import io.github.dhruvrawatdev.expressify.middleware.serve_favicon.FaviconOptions;
import io.github.dhruvrawatdev.expressify.middleware.serve_favicon.ServeFavicon;
import io.github.dhruvrawatdev.expressify.middleware.vhost.VHost;
import io.github.dhruvrawatdev.expressify.middleware.dev_error_handler.DevErrorHandler;
import io.github.dhruvrawatdev.expressify.middleware.dev_error_handler.DevErrorHandlerOptions;
import io.github.dhruvrawatdev.expressify.middleware.serve_index.ServeIndex;
import io.github.dhruvrawatdev.expressify.middleware.serve_index.ServeIndexOptions;
import io.github.dhruvrawatdev.expressify.middleware.timeout.Timeout;
import io.github.dhruvrawatdev.expressify.middleware.timeout.TimeoutOptions;
import io.github.dhruvrawatdev.expressify.middleware.session.SessionMiddleware;
import io.github.dhruvrawatdev.expressify.middleware.proxy.ProxyMiddleware;
import io.github.dhruvrawatdev.expressify.middleware.proxy.ProxyOptions;
import io.github.dhruvrawatdev.expressify.middleware.session.SessionOptions;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;
import io.github.dhruvrawatdev.expressify.template.engines.*;
import io.github.dhruvrawatdev.expressify.websocket.WsConnectionHandler;
import io.github.dhruvrawatdev.expressify.websocket.WsRegistry;
import io.github.dhruvrawatdev.expressify.websocket.WsServer;
import io.github.dhruvrawatdev.expressify.websocket.WsServerOptions;
import io.github.dhruvrawatdev.expressify.websocket.WsUpgradeHandler;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Core entry point for the Expressify framework — mirrors the Express.js {@code app} object.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * Expressify app = new Expressify();
 *
 * // Global middleware (applied to every request)
 * app.use(Expressify.json());           // parse JSON bodies
 * app.use(Expressify.cors());           // CORS headers
 * app.use(Expressify.morgan("dev"));    // request logging
 *
 * // Route with a path parameter
 * app.get("/users/:id", (req, res) -> {
 *     String id = req.param("id");
 *     res.json(Map.of("id", id, "name", "Alice"));
 * });
 *
 * // POST route — body available via req.body()
 * app.post("/users", (req, res) -> {
 *     Map<?,?> body = req.body();
 *     res.status(201).json(body);
 * });
 *
 * // Global error handler — register LAST
 * app.error((err, req, res, next) ->
 *     res.status(500).json(Map.of("error", err.getMessage()))
 * );
 *
 * app.listen(3000, () -> System.out.println("Listening on port 3000"));
 * }</pre>
 *
 * <h2>Handler types</h2>
 * <ul>
 *   <li>{@link io.github.dhruvrawatdev.expressify.router.handler.RouteHandler RouteHandler} —
 *       {@code (req, res, next) -> void} — must call {@code next.run()} to pass control
 *       to the next handler, or {@code next.error(t)} to skip to the error handler</li>
 *   <li>{@link io.github.dhruvrawatdev.expressify.router.handler.SimpleHandler SimpleHandler} —
 *       {@code (req, res) -> void} — two-param terminating handler; the framework automatically
 *       calls {@code next()} when the response is not yet committed after the handler returns</li>
 *   <li>{@link io.github.dhruvrawatdev.expressify.router.handler.AsyncRouteHandler AsyncRouteHandler} —
 *       returns {@code CompletableFuture<Void>}; any {@code CompletionException} is routed
 *       to error handlers automatically without additional try/catch</li>
 *   <li>{@link io.github.dhruvrawatdev.expressify.router.handler.AsyncSimpleHandler AsyncSimpleHandler} —
 *       async two-param variant (no {@code next}); exceptions propagate to error handlers</li>
 * </ul>
 *
 * <h2>Unlimited middleware</h2>
 * <pre>{@code
 * // Inline — up to two explicit middleware before a SimpleHandler
 * app.get("/admin", authMw, (req, res) -> res.send("Admin"));
 * app.get("/admin", authMw, logMw, (req, res) -> res.send("Admin"));
 *
 * // Array form — unlimited middleware
 * app.get("/admin", new RouteHandler[]{auth, log, audit}, (req, res) -> res.send("OK"));
 *
 * // List form — unlimited middleware
 * app.get("/admin", List.of(auth, log, audit), (req, res) -> res.send("OK"));
 *
 * // Varargs — unlimited RouteHandlers (each must call next() except the last)
 * app.get("/admin", auth, log, audit, (req, res, next) -> res.send("OK"));
 * }</pre>
 *
 * <h2>Async routes</h2>
 * <pre>{@code
 * // Named async sugar (preferred)
 * app.getAsync("/users", (req, res) ->
 *     db.findAllAsync().thenAccept(users -> res.json(users))
 * );
 *
 * // Async + unlimited sync middleware
 * app.getAsync("/users", List.of(auth, log), (req, res) ->
 *     db.findAllAsync().thenAccept(users -> res.json(users))
 * );
 * }</pre>
 *
 * <h2>Sub-routers</h2>
 * <pre>{@code
 * Router usersRouter = new Router();
 * usersRouter.get("/",    (req, res) -> res.json(users));
 * usersRouter.post("/",   (req, res) -> { ... });
 * usersRouter.delete("/:id", (req, res) -> { ... });
 *
 * app.use("/users", usersRouter);  // mounted at /users
 * }</pre>
 *
 * @see Router      for modular sub-routers
 * @see RouteBuilder for fluent multi-method chaining via {@code app.route(path)}
 */
public class Expressify {

    private final RouteRegistry              registry   = new RouteRegistry();
    private final Map<String, Object>        settings   = new HashMap<>();
    private final Map<String, TemplateEngine> engines   = new HashMap<>();
    private final WsRegistry                 wsRegistry = new WsRegistry();
    private volatile io.github.dhruvrawatdev.expressify.socket.ExpressifyIO ioInstance = null;

    /** App-level locals shared across all requests and templates (mirrors app.locals). */
    public final Map<String, Object> locals = new HashMap<>();

    private Undertow server;

    public Expressify() {
        // Register built-in template engines
        engines.put("thymeleaf",   new ThymeleafEngine());
        engines.put("pebble",      new PebbleEngine());
        engines.put("jte",         new JteEngine());
        engines.put("freemarker",  new FreeMarkerEngine());
        engines.put("handlebars",  new HandlebarsEngine());
        engines.put("velocity",    new VelocityEngine());

        // Default settings
        settings.put("view engine", "thymeleaf");
        settings.put("views", "src/main/resources/templates");
        settings.put("x-powered-by", true);
        settings.put("etag", true);

        // Make app.locals available to res.render() via settings
        settings.put("__locals", locals);
    }

    // Settings

    /**
     * Set a named framework setting.
     *
     * <p>Built-in setting keys:
     * <table border="1">
     *   <tr><th>Key</th><th>Type</th><th>Default</th><th>Description</th></tr>
     *   <tr><td>{@code "view engine"}</td><td>String</td><td>{@code "thymeleaf"}</td>
     *       <td>Active template engine — {@code "thymeleaf"}, {@code "pebble"}, {@code "freemarker"},
     *           {@code "jte"}, {@code "handlebars"}, or {@code "velocity"}</td></tr>
     *   <tr><td>{@code "views"}</td><td>String</td><td>{@code "src/main/resources/templates"}</td>
     *       <td>Path to the templates directory</td></tr>
     *   <tr><td>{@code "x-powered-by"}</td><td>Boolean</td><td>{@code true}</td>
     *       <td>Emit {@code X-Powered-By: Expressify} response header</td></tr>
     *   <tr><td>{@code "etag"}</td><td>Boolean</td><td>{@code true}</td>
     *       <td>Generate {@code ETag} headers on static responses</td></tr>
     * </table>
     *
     * <pre>{@code
     * app.set("view engine", "pebble");
     * app.set("views", "src/main/resources/views");
     * app.set("x-powered-by", false);
     * }</pre>
     *
     * @param key   the setting name (see table above)
     * @param value the value to store (use {@link Boolean} for flag settings)
     * @return this instance for chaining
     */
    public Expressify set(String key, Object value) {
        settings.put(key, value);
        return this;
    }

    /**
     * Retrieve a named framework setting value.
     *
     * <pre>{@code
     * String engine = (String) app.get("view engine"); // "thymeleaf"
     * boolean etag  = (boolean) app.get("etag");       // true
     * }</pre>
     *
     * @param key the setting name
     * @return the stored value, or {@code null} if the setting has not been set
     */
    public Object get(String key) {
        return settings.get(key);
    }

    /**
     * Enable a boolean setting by setting it to {@code true}.
     *
     * <pre>{@code app.enable("etag"); }</pre>
     *
     * @param key the setting name
     * @return this instance for chaining
     */
    public Expressify enable(String key) {
        settings.put(key, true);
        return this;
    }

    /**
     * Disable a boolean setting by setting it to {@code false}.
     *
     * <pre>{@code app.disable("x-powered-by"); }</pre>
     *
     * @param key the setting name
     * @return this instance for chaining
     */
    public Expressify disable(String key) {
        settings.put(key, false);
        return this;
    }

    /**
     * Register a callback triggered when a named route parameter is bound.
     * Runs before the route handlers for any route that contains the parameter.
     *
     * <pre>{@code
     * app.param("id", (req, res, next, id) -> {
     *     req.locals().put("user", findUser(id));
     *     next.run();
     * });
     * }</pre>
     *
     * @param name    the route parameter name (without the colon)
     * @param handler called with (req, res, next, value) before the route handler
     */
    public Expressify param(String name, ParamHandler handler) {
        registry.addParamHandler(name, handler);
        return this;
    }

    /**
     * Async variant of {@link #param(String, ParamHandler)} — use when the param
     * pre-processing involves async I/O (e.g. a database lookup).
     *
     * <pre>{@code
     * app.param("id", (AsyncParamHandler)(req, res, next, id) ->
     *     db.findUserAsync(id)
     *         .thenAccept(user -> {
     *             req.locals().put("user", user);
     *             try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
     *         })
     * );
     * }</pre>
     */
    public Expressify param(String name, AsyncParamHandler handler) {
        registry.addParamHandler(name, handler);
        return this;
    }

    /**
     * Returns {@code true} if the named boolean setting is explicitly set to {@code Boolean.TRUE}.
     *
     * <pre>{@code
     * if (app.enabled("etag")) { ... }
     * }</pre>
     *
     * @param key the setting name
     * @return {@code true} if the setting equals {@code Boolean.TRUE}
     */
    public boolean enabled(String key) {
        Object v = settings.get(key);
        return Boolean.TRUE.equals(v);
    }

    /**
     * Returns {@code true} if the named boolean setting is not set or set to {@code false}.
     *
     * <pre>{@code
     * if (app.disabled("x-powered-by")) { ... }
     * }</pre>
     *
     * @param key the setting name
     * @return {@code true} if the setting is not equal to {@code Boolean.TRUE}
     */
    public boolean disabled(String key) {
        return !enabled(key);
    }

    /**
     * Register a custom template engine for the given extension / key.
     *
     * <p>The following engines are pre-registered and available without calling this method:
     * {@code thymeleaf}, {@code pebble}, {@code freemarker}, {@code jte},
     * {@code handlebars}, {@code velocity}.
     *
     * <pre>{@code
     * app.engine("mustache", new MyMustacheEngine());
     * app.set("view engine", "mustache");
     * // res.render("index", model) will look for index.mustache
     * }</pre>
     *
     * @param ext    engine key / file extension (case-insensitive, e.g. {@code "pebble"})
     * @param engine the {@link TemplateEngine} implementation to associate with this key
     * @return this instance for chaining
     */
    public Expressify engine(String ext, TemplateEngine engine) {
        engines.put(ext.toLowerCase(), engine);
        return this;
    }

    // HTTP Methods
    // Each method has four call styles, mirroring Express.js:
    //
    //   app.get("/p", handler)                          — SimpleHandler (no next)
    //   app.get("/p", mw, handler)                      — 1 middleware + SimpleHandler
    //   app.get("/p", mw1, mw2, handler)                — 2 middleware + SimpleHandler
    //   app.get("/p", new RouteHandler[]{...}, handler) — unlimited middleware + SimpleHandler
    //   app.get("/p", List.of(...), handler)            — unlimited middleware + SimpleHandler (List form)
    //   app.get("/p", mw1, mw2, mw3, ...)              — unlimited RouteHandlers via varargs

    private static List<RouteHandler> buildList(RouteHandler[] mws, SimpleHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.length + 1);
        list.addAll(Arrays.asList(mws));
        list.add(wrap(terminal));
        return list;
    }

    private static List<RouteHandler> buildList(List<RouteHandler> mws, SimpleHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.size() + 1);
        list.addAll(mws);
        list.add(wrap(terminal));
        return list;
    }

    /** Builds a handler list where the terminal is already a fully-wrapped {@link RouteHandler} (used by async overloads). */
    private static List<RouteHandler> buildListR(RouteHandler[] mws, RouteHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.length + 1);
        list.addAll(Arrays.asList(mws));
        list.add(terminal);
        return list;
    }

    private static List<RouteHandler> buildListR(List<RouteHandler> mws, RouteHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.size() + 1);
        list.addAll(mws);
        list.add(terminal);
        return list;
    }

    /**
     * Register a GET route handler using varargs — accepts any mix of
     * {@link RouteHandler}s (including async handlers via explicit cast).
     *
     * <pre>{@code
     * // Single three-param handler
     * app.get("/users", (req, res, next) -> res.json(users));
     *
     * // Middleware chain — each handler must call next.run() to continue
     * app.get("/users", auth, log, (req, res, next) -> res.json(users));
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders, wildcards ({@code *}),
     *                 and optional segments
     * @param handlers one or more {@link RouteHandler}s executed in order; the last should
     *                 send a response (or call {@code next.run()} to fall through)
     * @return this instance for chaining
     */
    public Expressify get(String path, RouteHandler... handlers) {
        registry.addRoute("GET", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a GET route with a two-param {@link SimpleHandler}.
     * The framework automatically calls {@code next()} if the response is not committed on return.
     *
     * <pre>{@code app.get("/users", (req, res) -> res.json(users)); }</pre>
     *
     * @param path    route path
     * @param handler terminal handler (no {@code next} parameter needed)
     * @return this instance for chaining
     */
    public Expressify get(String path, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a GET route with one middleware and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code app.get("/admin", authMw, (req, res) -> res.send("Admin area")); }</pre>
     *
     * @param path    route path
     * @param mw      middleware executed first; must call {@code next.run()} to proceed
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify get(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a GET route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code app.get("/admin", authMw, logMw, (req, res) -> res.send("Admin area")); }</pre>
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify get(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a GET route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.get("/admin", new RouteHandler[]{auth, log, audit}, (req, res) -> res.send("OK"));
     * }</pre>
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers; each must call {@code next.run()}
     * @param handler     terminal handler executed after all middleware
     * @return this instance for chaining
     */
    public Expressify get(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("GET", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a GET route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.get("/admin", List.of(auth, log, audit), (req, res) -> res.send("OK"));
     * }</pre>
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers; each must call {@code next.run()}
     * @param handler     terminal handler executed after all middleware
     * @return this instance for chaining
     */
    public Expressify get(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("GET", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a POST route handler.
     * POST is typically used for creating resources or submitting form data.
     *
     * <pre>{@code
     * app.post("/users", (req, res, next) -> {
     *     Map<?,?> body = req.body();   // requires Expressify.json() middleware
     *     res.status(201).json(body);
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify post(String path, RouteHandler... handlers) {
        registry.addRoute("POST", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a POST route with a two-param {@link SimpleHandler}.
     *
     * <pre>{@code app.post("/users", (req, res) -> res.status(201).json(req.body())); }</pre>
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify post(String path, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a POST route with one middleware and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code app.post("/users", validateMw, (req, res) -> res.status(201).json(req.body())); }</pre>
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify post(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a POST route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path route path
     * @param mw1 first middleware
     * @param mw2 second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify post(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a POST route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.post("/users", new RouteHandler[]{auth, validate, audit}, (req, res) -> res.status(201).json(req.body()));
     * }</pre>
     *
     * @param path route path
     * @param middlewares ordered array of middleware handlers
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify post(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("POST", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a POST route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.post("/users", List.of(auth, validate, audit), (req, res) -> res.status(201).json(req.body()));
     * }</pre>
     *
     * @param path route path
     * @param middlewares ordered list of middleware handlers
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify post(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("POST", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PUT route handler.
     * PUT is typically used for full resource replacement.
     *
     * <pre>{@code
     * app.put("/users/:id", (req, res, next) -> {
     *     String id   = req.param("id");
     *     Map<?,?> body = req.body();
     *     res.json(update(id, body));
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify put(String path, RouteHandler... handlers) {
        registry.addRoute("PUT", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a PUT route with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify put(String path, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a PUT route with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify put(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a PUT route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify put(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a PUT route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify put(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("PUT", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PUT route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify put(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("PUT", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a DELETE route handler.
     *
     * <pre>{@code
     * app.delete("/users/:id", authMw, (req, res, next) -> {
     *     deleteUser(req.param("id"));
     *     res.status(204).send();
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify delete(String path, RouteHandler... handlers) {
        registry.addRoute("DELETE", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a DELETE route with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify delete(String path, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a DELETE route with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify delete(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a DELETE route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify delete(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a DELETE route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify delete(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("DELETE", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a DELETE route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify delete(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("DELETE", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PATCH route handler.
     * PATCH is used for partial resource updates (only the fields that change).
     *
     * <pre>{@code
     * app.patch("/users/:id", (req, res, next) -> {
     *     partialUpdate(req.param("id"), req.body());
     *     res.json(Map.of("updated", true));
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify patch(String path, RouteHandler... handlers) {
        registry.addRoute("PATCH", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a PATCH route with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify patch(String path, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a PATCH route with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify patch(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a PATCH route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify patch(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a PATCH route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify patch(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("PATCH", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PATCH route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify patch(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("PATCH", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register an OPTIONS route handler.
     * Usually handled automatically by {@link io.github.dhruvrawatdev.expressify.middleware.cors.Cors Cors}
     * middleware, but can be customised per-path here.
     *
     * <pre>{@code
     * app.options("/resource", (req, res) -> {
     *     res.header("Allow", "GET, POST, OPTIONS").status(204).send();
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify options(String path, RouteHandler... handlers) {
        registry.addRoute("OPTIONS", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register an OPTIONS route with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify options(String path, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register an OPTIONS route with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify options(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register an OPTIONS route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify options(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register an OPTIONS route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify options(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register an OPTIONS route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify options(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a HEAD route handler.
     * HEAD behaves identically to GET but the response body is omitted by the HTTP spec.
     * Use it to expose metadata (Content-Length, ETag, Last-Modified) without transferring the body.
     *
     * <pre>{@code
     * app.head("/files/:name", (req, res) -> {
     *     res.header("Content-Length", String.valueOf(getSize(req.param("name"))))
     *        .status(200).send();
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify head(String path, RouteHandler... handlers) {
        registry.addRoute("HEAD", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register a HEAD route with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify head(String path, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register a HEAD route with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify head(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register a HEAD route with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify head(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register a HEAD route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify head(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("HEAD", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a HEAD route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify head(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("HEAD", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a handler that matches <em>any</em> HTTP method at the given path.
     * Useful for applying path-scoped middleware that should run regardless of verb.
     *
     * <pre>{@code
     * // Log every request to /api regardless of method
     * app.all("/api/*", (req, res, next) -> {
     *     System.out.println(req.method() + " " + req.path());
     *     next.run();
     * });
     * }</pre>
     *
     * @param path     route path; supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this instance for chaining
     */
    public Expressify all(String path, RouteHandler... handlers) {
        registry.addRoute("ALL", path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Register an all-methods handler with a two-param {@link SimpleHandler}.
     *
     * @param path    route path
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify all(String path, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(wrap(handler)));
        return this;
    }

    /**
     * Register an all-methods handler with one middleware and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw      middleware executed first
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify all(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(mw, wrap(handler)));
        return this;
    }

    /**
     * Register an all-methods handler with two middleware handlers and a terminal {@link SimpleHandler}.
     *
     * @param path    route path
     * @param mw1     first middleware
     * @param mw2     second middleware
     * @param handler terminal handler
     * @return this instance for chaining
     */
    public Expressify all(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }

    /**
     * Register an all-methods handler with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered array of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify all(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("ALL", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register an all-methods handler with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * @param path        route path
     * @param middlewares ordered list of middleware handlers
     * @param handler     terminal handler
     * @return this instance for chaining
     */
    public Expressify all(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("ALL", path, buildList(middlewares, handler));
        return this;
    }

    // ── Async HTTP Methods (use getAsync/postAsync etc. for unambiguous registration) ──

    /**
     * Register an async GET handler.
     *
     * <pre>{@code
     * app.getAsync("/users", (req, res, next) ->
     *     db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param h    async handler; future rejection routes to the error handler automatically
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, AsyncRouteHandler h) { registry.addRoute("GET",     path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async POST — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify postAsync(String path, AsyncRouteHandler h) { registry.addRoute("POST",    path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async PUT — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify putAsync(String path, AsyncRouteHandler h) { registry.addRoute("PUT",     path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async DELETE — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify deleteAsync(String path, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async PATCH — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify patchAsync(String path, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async OPTIONS — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify optionsAsync(String path, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async HEAD — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify headAsync(String path, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, Arrays.asList(asyncWrap(h))); return this; }
    /** Async ALL-methods — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Expressify allAsync(String path, AsyncRouteHandler h) { registry.addRoute("ALL",     path, Arrays.asList(asyncWrap(h))); return this; }

    // AsyncSimpleHandler named sugar (use getAsync/postAsync etc.)

    /**
     * Register an async GET handler using the two-param {@link AsyncSimpleHandler} (no {@code next}).
     *
     * <pre>{@code
     * app.getAsync("/users", (req, res) ->
     *     db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param h    async two-param handler; future rejection routes to the error handler
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, AsyncSimpleHandler h) { registry.addRoute("GET", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async POST, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify postAsync(String path, AsyncSimpleHandler h) { registry.addRoute("POST", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async PUT, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify putAsync(String path, AsyncSimpleHandler h) { registry.addRoute("PUT", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async DELETE, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify deleteAsync(String path, AsyncSimpleHandler h) { registry.addRoute("DELETE", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async PATCH, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify patchAsync(String path, AsyncSimpleHandler h) { registry.addRoute("PATCH", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async OPTIONS, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify optionsAsync(String path, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async HEAD, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify headAsync(String path, AsyncSimpleHandler h) { registry.addRoute("HEAD", path, Arrays.asList(asyncWrap(h))); return this; }
    /** Named-sugar async ALL-methods, no-next — see {@link #getAsync(String, AsyncSimpleHandler)}. */
    public Expressify allAsync(String path, AsyncSimpleHandler h) { registry.addRoute("ALL", path, Arrays.asList(asyncWrap(h))); return this; }

    // Async + unlimited sync middleware — array form

    /**
     * Register an async GET route with unlimited sync middleware (array form) plus an async terminal handler.
     * Sync middleware runs first (each calling {@code next.run()}), then the async handler executes.
     *
     * <pre>{@code
     * app.getAsync("/users",
     *     new RouteHandler[]{authMw, logMw},
     *     (req, res, next) -> db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered array of sync middleware handlers
     * @param h    async terminal handler
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("GET",     path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async POST + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify postAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("POST",    path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PUT + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify putAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PUT",     path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async DELETE + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify deleteAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PATCH + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify patchAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async OPTIONS + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify optionsAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async HEAD + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify headAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async ALL-methods + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Expressify allAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("ALL",     path, buildListR(mws, asyncWrap(h))); return this; }

    /**
     * Register an async GET route with unlimited sync middleware (array form) plus a no-next async handler.
     *
     * <pre>{@code
     * app.getAsync("/users",
     *     new RouteHandler[]{authMw, logMw},
     *     (req, res) -> db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered array of sync middleware handlers
     * @param h    async two-param terminal handler (no {@code next})
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("GET", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async POST + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify postAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("POST", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PUT + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify putAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PUT", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async DELETE + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify deleteAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("DELETE",path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PATCH + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify patchAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PATCH", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async OPTIONS + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify optionsAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async HEAD + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify headAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h){ registry.addRoute("HEAD", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async ALL-methods + middleware array, no-next — see {@link #getAsync(String, RouteHandler[], AsyncSimpleHandler)}. */
    public Expressify allAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h){ registry.addRoute("ALL", path, buildListR(mws, asyncWrap(h))); return this; }

    // Async + unlimited sync middleware — List form

    /**
     * Register an async GET route with unlimited sync middleware (list form) plus an async terminal handler.
     *
     * <pre>{@code
     * app.getAsync("/users",
     *     List.of(authMw, logMw),
     *     (req, res, next) -> db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered list of sync middleware handlers
     * @param h    async terminal handler
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h){ registry.addRoute("GET", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async POST + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify postAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("POST", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PUT + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify putAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PUT", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async DELETE + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify deleteAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("DELETE", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PATCH + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify patchAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PATCH", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async OPTIONS + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify optionsAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async HEAD + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify headAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("HEAD", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async ALL-methods + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Expressify allAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("ALL", path, buildListR(mws, asyncWrap(h))); return this; }

    /**
     * Register an async GET route with unlimited sync middleware (list form) plus a no-next async handler.
     *
     * <pre>{@code
     * app.getAsync("/users",
     *     List.of(authMw, logMw),
     *     (req, res) -> db.findAllAsync().thenAccept(users -> res.json(users))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered list of sync middleware handlers
     * @param h    async two-param terminal handler (no {@code next})
     * @return this instance for chaining
     */
    public Expressify getAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("GET", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async POST + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify postAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("POST", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PUT + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify putAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PUT", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async DELETE + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify deleteAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("DELETE", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async PATCH + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify patchAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PATCH", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async OPTIONS + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify optionsAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async HEAD + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify headAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("HEAD", path, buildListR(mws, asyncWrap(h))); return this; }
    /** Async ALL-methods + middleware list, no-next — see {@link #getAsync(String, List, AsyncSimpleHandler)}. */
    public Expressify allAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("ALL", path, buildListR(mws, asyncWrap(h))); return this; }

    /**
     * Return a fluent {@link RouteBuilder} for chaining multiple HTTP methods on the same path.
     * Avoids repeating the path string when defining several verbs.
     *
     * <pre>{@code
     * app.route("/api/users")
     *    .get((req, res) -> res.json(users))
     *    .post((req, res) -> {
     *        users.add(req.body());
     *        res.status(201).json(req.body());
     *    });
     *
     * app.route("/api/users/:id")
     *    .get(getHandler)
     *    .put(updateHandler)
     *    .delete(deleteHandler);
     * }</pre>
     *
     * @param path the route path shared by all chained HTTP method handlers
     * @return a new {@link RouteBuilder} scoped to {@code path}
     */
    public RouteBuilder route(String path) {
        return new RouteBuilder(path, registry);
    }

    // ── Middleware ─────────────────────────────────────────────────────────

    /**
     * Apply one or more middleware handlers globally (every request passes through them).
     * Handlers are executed in registration order.
     *
     * <pre>{@code
     * app.use(Expressify.json());
     * app.use(Expressify.cors());
     * app.use(Expressify.morgan("dev"));
     *
     * // Custom middleware
     * app.use((req, res, next) -> {
     *     req.locals().put("startTime", System.currentTimeMillis());
     *     next.run();
     * });
     * }</pre>
     *
     * @param handlers one or more {@link RouteHandler}s; each must call {@code next.run()} to continue
     * @return this instance for chaining
     */
    public Expressify use(RouteHandler... handlers) {
        registry.addMiddleware(null, Arrays.asList(handlers));
        return this;
    }

    /**
     * Apply a two-param {@link SimpleHandler} globally.
     * The framework automatically calls {@code next()} if the response is not committed on return.
     *
     * <pre>{@code app.use((req, res) -> res.header("X-Custom", "value")); }</pre>
     *
     * @param handler the middleware handler
     * @return this instance for chaining
     */
    public Expressify use(SimpleHandler handler) {
        registry.addMiddleware(null, Arrays.asList(wrap(handler)));
        return this;
    }

    /**
     * Apply one or more middleware handlers only to requests whose path starts with {@code path}.
     * The matched prefix is <em>not</em> stripped before entering the handlers (unlike sub-routers).
     *
     * <pre>{@code
     * app.use("/api", rateLimiter, authMw);
     * app.use("/admin", adminAuthMw);
     * }</pre>
     *
     * @param path     path prefix — handlers run only when the request path starts with this value
     * @param handlers one or more {@link RouteHandler}s; each must call {@code next.run()} to continue
     * @return this instance for chaining
     */
    public Expressify use(String path, RouteHandler... handlers) {
        registry.addMiddleware(path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Apply a two-param {@link SimpleHandler} for requests whose path starts with {@code path}.
     *
     * @param path    path prefix
     * @param handler the middleware handler
     * @return this instance for chaining
     */
    public Expressify use(String path, SimpleHandler handler) {
        registry.addMiddleware(path, Arrays.asList(wrap(handler)));
        return this;
    }

    /**
     * Apply a list of middleware handlers globally, each registered individually.
     *
     * <pre>{@code
     * List<RouteHandler> stack = List.of(cors, helmet, logger);
     * app.use(stack);
     * }</pre>
     *
     * @param handlers list of {@link RouteHandler}s to register as global middleware
     * @return this instance for chaining
     */
    public Expressify use(List<RouteHandler> handlers) {
        handlers.forEach(h -> registry.addMiddleware(null, Arrays.asList(h)));
        return this;
    }

    /**
     * Mount a {@link Router} at the given path prefix.
     * All routes defined in the router become available under that prefix, and
     * the prefix is stripped from the path before entering the router's handler chain.
     *
     * <pre>{@code
     * Router usersRouter = new Router();
     * usersRouter.get("/",    (req, res) -> res.json(users));
     * usersRouter.get("/:id", (req, res) -> res.json(find(req.param("id"))));
     *
     * app.use("/users", usersRouter);
     * // GET /users     → usersRouter's "/"   handler
     * // GET /users/42  → usersRouter's "/:id" handler
     * }</pre>
     *
     * @param path   the mount path prefix
     * @param router the {@link Router} to mount
     * @return this instance for chaining
     */
    public Expressify use(String path, Router router) {
        registry.addRouter(path, router);
        return this;
    }

    // Async Middleware

    /**
     * Apply async middleware globally via explicit cast or typed variable.
     * Future rejection automatically calls {@code next(err)}.
     *
     * <pre>{@code
     * app.use((AsyncRouteHandler)(req, res, next) ->
     *     tokenService.validateAsync(req.get("Authorization"))
     *         .thenRun(() -> next.run())
     * );
     * }</pre>
     *
     * @param handler async middleware handler
     * @return this instance for chaining
     */
    // Async Middleware (use useAsync)

    /**
     * Apply async middleware globally. Use {@code useAsync()} for unambiguous lambda registration.
     * Rejected futures automatically call {@code next(err)}.
     *
     * <pre>{@code
     * app.useAsync((req, res, next) ->
     *     db.touchLastSeen(req.get("X-User-Id")).thenRun(() -> next.run())
     * );
     * }</pre>
     *
     * @param handler async middleware handler
     * @return this instance for chaining
     */
    public Expressify useAsync(AsyncRouteHandler handler) {
        registry.addMiddleware(null, Arrays.asList(asyncWrap(handler)));
        return this;
    }

    /**
     * Apply async middleware globally (no-next variant).
     *
     * @param handler async two-param middleware handler
     * @return this instance for chaining
     */
    public Expressify useAsync(AsyncSimpleHandler handler) {
        registry.addMiddleware(null, Arrays.asList(asyncWrap(handler)));
        return this;
    }

    /**
     * Apply async middleware under a path prefix.
     *
     * @param path    path prefix
     * @param handler async middleware handler
     * @return this instance for chaining
     */
    public Expressify useAsync(String path, AsyncRouteHandler handler) {
        registry.addMiddleware(path, Arrays.asList(asyncWrap(handler)));
        return this;
    }

    /**
     * Apply async middleware under a path prefix (no-next variant).
     *
     * @param path    path prefix
     * @param handler async two-param middleware handler
     * @return this instance for chaining
     */
    public Expressify useAsync(String path, AsyncSimpleHandler handler) {
        registry.addMiddleware(path, Arrays.asList(asyncWrap(handler)));
        return this;
    }

    // Error Handler

    /**
     * Register the global error handler.
     *
     * <p>The error handler is invoked when any handler calls {@code next.error(t)}
     * or throws an uncaught exception. Register it <strong>last</strong>, after all routes
     * and middleware, so it can catch errors from the entire chain.
     *
     * <pre>{@code
     * app.error((err, req, res, next) -> {
     *     if (err instanceof NotFoundException) {
     *         res.status(404).json(Map.of("error", err.getMessage()));
     *     } else {
     *         res.status(500).json(Map.of("error", "Internal Server Error"));
     *     }
     * });
     * }</pre>
     *
     * @param handler the four-param error handler: {@code (err, req, res, next) -> void}
     * @return this instance for chaining
     */
    public Expressify error(ErrorHandler handler) {
        registry.addErrorHandler(handler);
        return this;
    }

    /**
     * Register an async error handler via explicit cast or typed variable.
     * The future's rejection is silently swallowed after {@code next.error()} is called —
     * do not forget to send a response inside the handler.
     *
     * <pre>{@code
     * app.error((AsyncErrorHandler)(err, req, res, next) ->
     *     auditLog.writeAsync(err)
     *         .thenRun(() -> res.status(500).json(Map.of("error", err.getMessage())))
     * );
     * }</pre>
     *
     * @param handler async four-param error handler returning {@code CompletableFuture<Void>}
     * @return this instance for chaining
     */
    public Expressify errorAsync(AsyncErrorHandler handler) {
        return error(asyncWrapError(handler));
    }

    // Server

    /**
     * Start the HTTP server on the given port, binding to all interfaces ({@code 0.0.0.0}).
     * Returns immediately after Undertow starts — the server runs on background I/O threads.
     *
     * <pre>{@code app.listen(3000); }</pre>
     *
     * @param port TCP port to listen on (e.g. {@code 3000})
     */
    public void listen(int port) {
        listen(port, null);
    }

    /**
     * Start the HTTP server and invoke a callback once it is accepting connections.
     *
     * <pre>{@code
     * app.listen(3000, () -> System.out.println("Server ready on http://localhost:3000"));
     * }</pre>
     *
     * @param port     TCP port to listen on
     * @param callback invoked on the calling thread immediately after the server starts;
     *                 may be {@code null}
     */
    public void listen(int port, Runnable callback) {
        Logger.getLogger("io.undertow").setLevel(Level.WARNING);
        Logger.getLogger("org.xnio").setLevel(Level.WARNING);
        Logger.getLogger("org.jboss").setLevel(Level.WARNING);

        UndertowAdapter adapter = new UndertowAdapter(registry, settings, engines);
        io.undertow.server.HttpHandler wsLayer = wsRegistry.isEmpty()
                ? adapter
                : new WsUpgradeHandler(wsRegistry, adapter);
        io.undertow.server.HttpHandler topHandler = (ioInstance != null)
                ? ioInstance.createUpgradeHandler(wsLayer)
                : wsLayer;

        server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE,           Long.MAX_VALUE)
                .setServerOption(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, Long.MAX_VALUE)
                .setHandler(topHandler)
                .build();
        server.start();
        if (callback != null) callback.run();
    }

    /**
     * Alias for {@link #listen(int)} — start the server on the given port.
     *
     * @param port TCP port to listen on
     */
    public void run(int port) { listen(port, null); }

    /**
     * Alias for {@link #listen(int, Runnable)} — start the server and invoke a callback when ready.
     *
     * @param port     TCP port to listen on
     * @param callback invoked after the server starts; may be {@code null}
     */
    public void run(int port, Runnable callback) { listen(port, callback); }

    /**
     * Stop the HTTP server gracefully.
     * Has no effect if the server was never started.
     *
     * <pre>{@code
     * Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
     * }</pre>
     */
    public void stop() {
        if (server != null) server.stop();
        wsRegistry.servers().forEach(WsServer::notifyClose);
        if (ioInstance != null) ioInstance.close();
    }

    /**
     * Attach a {@link io.github.dhruvrawatdev.expressify.socket.ExpressifyIO} instance.
     * Called internally by {@code ExpressifyIO.attach(app)}; prefer using that method.
     *
     * @param io the Socket.IO server to attach
     */
    public void ioAttach(io.github.dhruvrawatdev.expressify.socket.ExpressifyIO io) {
        this.ioInstance = io;
    }

    // WebSocket

    /**
     * Register a WebSocket endpoint at {@code path}.
     * The handler is called once per accepted connection — attach listeners inside it.
     * Equivalent to the Node.js {@code express-ws} pattern:
     * {@code app.ws('/path', (ws, req) => { ws.on('message', fn); })}.
     *
     * <pre>{@code
     * app.ws("/echo", (ws, req) -> {
     *     ws.onMessage(msg -> ws.send("Echo: " + msg.asText()));
     *     ws.onClose(ev  -> System.out.println("Client disconnected"));
     * });
     * }</pre>
     *
     * <p>Path parameters work the same as HTTP routes:
     * <pre>{@code
     * app.ws("/chat/:room", (ws, req) -> {
     *     String room = req.param("room");
     *     ws.onMessage(msg -> ws.send("[" + room + "] " + msg.asText()));
     * });
     * }</pre>
     *
     * @param path    WebSocket path; supports {@code :param} placeholders and wildcards
     * @param handler called on every new connection; register message/close/error listeners here
     * @return this instance for chaining
     */
    public Expressify ws(String path, WsConnectionHandler handler) {
        wsRegistry.register(new WsServer(path, handler, WsServerOptions.DEFAULT));
        return this;
    }

    /**
     * Register a WebSocket endpoint with custom server options.
     *
     * <pre>{@code
     * app.ws("/secure",
     *     WsServerOptions.builder()
     *         .verifyClient(info -> validateToken(info.req().get("Authorization")))
     *         .maxPayload(64 * 1024)
     *         .build(),
     *     (ws, req) -> {
     *         ws.onMessage(msg -> ws.send(msg.asText()));
     *     }
     * );
     * }</pre>
     *
     * @param path    WebSocket path
     * @param options server-level options (max payload, client tracking, verifyClient, etc.)
     * @param handler connection handler
     * @return this instance for chaining
     */
    public Expressify ws(String path, WsServerOptions options, WsConnectionHandler handler) {
        wsRegistry.register(new WsServer(path, handler, options));
        return this;
    }

    /**
     * Create and register a {@link WsServer} at {@code path} with default options.
     * Returns the server so you can configure it fluently, broadcast, and query clients.
     *
     * <p>Equivalent to constructing a {@code ws.WebSocketServer} with {@code { path }} in Node.js.
     *
     * <pre>{@code
     * WsServer chat = app.wsServer("/chat");
     *
     * chat.onConnection((ws, req) -> {
     *     String name = req.query("name");
     *     ws.onMessage(msg -> chat.broadcast("[" + name + "]: " + msg.asText()));
     *     ws.onClose(ev  -> chat.broadcast(name + " left"));
     * });
     *
     * // Heartbeat — schedule this with a ScheduledExecutorService
     * chat.clients().forEach(ws -> {
     *     if (!ws.isAlive()) { ws.terminate(); return; }
     *     ws.setAlive(false);
     *     ws.ping();
     * });
     * }</pre>
     *
     * @param path WebSocket path; supports {@code :param} placeholders and wildcards
     * @return the registered {@link WsServer}
     */
    public WsServer wsServer(String path) {
        return wsServer(path, WsServerOptions.DEFAULT);
    }

    /**
     * Create and register a {@link WsServer} at {@code path} with custom options.
     * Returns the server for fluent configuration, broadcasting, and client tracking.
     *
     * <pre>{@code
     * WsServer wss = app.wsServer("/data",
     *     WsServerOptions.builder()
     *         .clientTracking(true)
     *         .maxPayload(1024 * 1024)
     *         .subprotocols("json.v1", "json.v2")
     *         .build()
     * );
     * wss.onConnection((ws, req) -> {
     *     ws.onMessage(msg -> wss.broadcast(msg.asText(), ws));
     * });
     * }</pre>
     *
     * @param path    WebSocket path
     * @param options server-level options
     * @return the registered {@link WsServer}
     */
    public WsServer wsServer(String path, WsServerOptions options) {
        WsServer server = new WsServer(path, options);
        wsRegistry.register(server);
        return server;
    }

    /**
     * Register a pre-configured {@link WsServer} instance.
     * Use this when you construct the server separately before registration.
     *
     * <pre>{@code
     * WsServer wss = new WsServer("/ws", WsServerOptions.builder().build());
     * wss.onConnection((ws, req) -> ws.onMessage(msg -> ws.send(msg.asText())));
     * app.ws(wss);
     * }</pre>
     *
     * @param server a fully configured {@link WsServer}
     * @return this instance for chaining
     */
    public Expressify ws(WsServer server) {
        wsRegistry.register(server);
        return this;
    }

    // Built-in Middleware Factories

    /**
     * JSON body parser — validates Content-Type, enforces 100 KB limit.
     * Equivalent to {@code express.json()}.
     */
    public static RouteHandler json() {
        return BodyParser.json();
    }

    /**
     * JSON body parser with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.json(JsonOptions.builder().limit(512 * 1024).build()));
     * }</pre>
     */
    public static RouteHandler json(JsonOptions opts) {
        return BodyParser.json(opts);
    }

    /**
     * URL-encoded body parser with default options — equivalent to
     * {@code express.urlencoded({ extended: true })}.
     */
    public static RouteHandler urlencoded() {
        return BodyParser.urlencoded();
    }

    /**
     * URL-encoded body parser with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.urlencoded(UrlencodedOptions.builder().extended(false).build()));
     * }</pre>
     */
    public static RouteHandler urlencoded(UrlencodedOptions opts) {
        return BodyParser.urlencoded(opts);
    }

    /**
     * Plain-text body parser with default options — equivalent to {@code express.text()}.
     * Body accessible via {@code req.text()}.
     */
    public static RouteHandler text() {
        return BodyParser.text();
    }

    /**
     * Plain-text body parser with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.text(TextOptions.builder()
     *     .type("text/csv")
     *     .defaultCharset("iso-8859-1")
     *     .limit("500kb")
     *     .build()));
     * }</pre>
     */
    public static RouteHandler text(TextOptions opts) {
        return BodyParser.text(opts);
    }

    /**
     * Raw binary body parser with default options — equivalent to {@code express.raw()}.
     * Body accessible via {@code req.rawBytes()}.
     */
    public static RouteHandler raw() {
        return BodyParser.raw();
    }

    /**
     * Raw binary body parser with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.raw(RawOptions.builder()
     *     .type("application/pdf")
     *     .limit("5mb")
     *     .build()));
     * }</pre>
     */
    public static RouteHandler raw(RawOptions opts) {
        return BodyParser.raw(opts);
    }

    /**
     * Cookie parser — parses the Cookie header into {@code req.cookies()}.
     * Plain cookies only (no signing).
     */
    public static RouteHandler cookieParser() {
        return CookieParser.create();
    }

    /**
     * Cookie parser with a signing secret — populates {@code req.signedCookies()}
     * with HMAC-verified cookies and stores the secret on {@code req.secret()}.
     *
     * <pre>{@code app.use(Expressify.cookieParser("my-secret")); }</pre>
     */
    public static RouteHandler cookieParser(String secret) {
        return CookieParser.create(secret);
    }

    /**
     * Request logger middleware -- delegates to Morgan.
     *
     * @param format one of: dev, combined, common, short, tiny
     */
    public static RouteHandler logger(String format) {
        return Morgan.create(format);
    }

    /**
     * Morgan request logger with default dev format (coloured output).
     *
     * <pre>{@code app.use(Expressify.morgan()); }</pre>
     */
    public static RouteHandler morgan() {
        return Morgan.dev();
    }

    /**
     * Morgan request logger with a specific format.
     *
     * <pre>{@code app.use(Expressify.morgan("combined")); }</pre>
     */
    public static RouteHandler morgan(String format) {
        return Morgan.create(format);
    }

    /**
     * Morgan request logger with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.morgan("tiny",
     *     MorganOptions.builder().skip((req, res) -> res.getStatus() < 400).build()));
     * }</pre>
     */
    public static RouteHandler morgan(String format, MorganOptions options) {
        return Morgan.create(format, options);
    }

    /**
     * HTTP method override -- allows POST clients to tunnel DELETE/PUT/PATCH
     * using the X-HTTP-Method-Override header.
     *
     * <pre>{@code app.use(Expressify.methodOverride()); }</pre>
     */
    public static RouteHandler methodOverride() {
        return MethodOverride.create();
    }

    /**
     * HTTP method override using a custom header or query key.
     *
     * <pre>{@code app.use(Expressify.methodOverride("_method")); // ?_method=DELETE }</pre>
     */
    public static RouteHandler methodOverride(String getter) {
        return MethodOverride.create(getter);
    }

    /**
     * HTTP method override with custom options.
     */
    public static RouteHandler methodOverride(String getter, MethodOverrideOptions options) {
        return MethodOverride.create(getter, options);
    }

    /**
     * Response time header middleware -- adds X-Response-Time to every response.
     *
     * <pre>{@code app.use(Expressify.responseTime()); }</pre>
     */
    public static RouteHandler responseTime() {
        return ResponseTime.create();
    }

    /**
     * Response time header middleware with custom options.
     */
    public static RouteHandler responseTime(ResponseTimeOptions options) {
        return ResponseTime.create(options);
    }

    /**
     * Reject requests whose Content-Length exceeds {@code size}.
     *
     * <pre>{@code app.use(Expressify.bodyLimit("10mb")); }</pre>
     */
    public static RouteHandler bodyLimit(String size) {
        long maxBytes = BodyParser.parseSize(size);
        return (req, res, next) -> {
            String cl = req.get("Content-Length");
            if (cl != null) {
                try {
                    long len = Long.parseLong(cl.trim());
                    if (len > maxBytes) {
                        res.status(413).json(Map.of("error", "Payload Too Large",
                                "limit", size, "received", len));
                        return;
                    }
                } catch (NumberFormatException ignored) {}
            }
            next.run();
        };
    }

    /**
     * Static file middleware — equivalent to {@code express.static(root)}.
     *
     * <pre>{@code app.use(Expressify.static_("public")); }</pre>
     */
    public static RouteHandler static_(String root) {
        return ServeStatic.serve(root);
    }

    /**
     * Static file middleware with options.
     *
     * <pre>{@code
     * app.use("/assets", Expressify.static_("public",
     *     StaticOptions.builder().maxAge(86400).build()));
     * }</pre>
     */
    public static RouteHandler static_(String root, StaticOptions opts) {
        return ServeStatic.serve(root, opts);
    }

    /**
     * Session middleware — equivalent to {@code express-session}.
     *
     * <pre>{@code
     * app.use(Expressify.session(SessionOptions.builder()
     *     .secret("my-secret")
     *     .maxAge(3600)
     *     .build()));
     * }</pre>
     */
    public static RouteHandler session(SessionOptions opts) {
        return SessionMiddleware.configure(opts);
    }

    /**
     * Helmet security headers with sensible production defaults.
     * Equivalent to {@code helmet()} in Express.js.
     *
     * <pre>{@code app.use(Expressify.helmet()); }</pre>
     */
    public static RouteHandler helmet() {
        return Helmet.defaults();
    }

    /**
     * Helmet security headers with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.helmet(HelmetOptions.builder()
     *     .frameguard("DENY")
     *     .hsts(true)
     *     .build()));
     * }</pre>
     */
    public static RouteHandler helmet(HelmetOptions opts) {
        return Helmet.configure(opts);
    }

    /**
     * CORS middleware with permissive defaults — equivalent to {@code cors()} in Express.js.
     *
     * <pre>{@code app.use(Expressify.cors()); }</pre>
     */
    public static RouteHandler cors() {
        return Cors.all();
    }

    /**
     * CORS middleware with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.cors(CorsOptions.builder()
     *     .origin("https://example.com")
     *     .methods("GET,POST")
     *     .build()));
     * }</pre>
     */
    public static RouteHandler cors(CorsOptions opts) {
        return Cors.configure(opts);
    }

    /**
     * Response compression middleware with sensible defaults (gzip, 1 KB threshold).
     * Equivalent to {@code compression()} in Express.js.
     *
     * <pre>{@code app.use(Expressify.compression()); }</pre>
     */
    public static RouteHandler compression() {
        return Compression.defaults();
    }

    /**
     * Response compression middleware with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.compression(CompressionOptions.builder()
     *     .threshold(512)
     *     .level(9)
     *     .build()));
     * }</pre>
     */
    public static RouteHandler compression(CompressionOptions opts) {
        return Compression.configure(opts);
    }

    /**
     * Rate-limiting middleware with defaults (100 req / 60 s per IP).
     * Equivalent to {@code express-rate-limit} in Express.js.
     *
     * <pre>{@code app.use(Expressify.rateLimiter()); }</pre>
     */
    public static RouteHandler rateLimiter() {
        return RateLimiter.defaults();
    }

    /**
     * Rate-limiting middleware with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.rateLimiter(RateLimiterOptions.builder()
     *     .windowMs(15 * 60 * 1000)
     *     .max(100)
     *     .message("Too many requests, please try again later.")
     *     .build()));
     * }</pre>
     */
    public static RouteHandler rateLimiter(RateLimiterOptions opts) {
        return RateLimiter.configure(opts);
    }

    /**
     * Stateless cookie-based session — all session data lives in a signed cookie.
     * Equivalent to {@code cookie-session} in Express.js.
     *
     * <pre>{@code
     * app.use(Expressify.cookieSession("my-secret"));
     * // In handler:
     * Map<String, Object> sess = CookieSession.session(req);
     * sess.put("userId", 42);
     * }</pre>
     */
    public static RouteHandler cookieSession(String secret) {
        return CookieSession.create(secret);
    }

    /** Stateless cookie-based session with full options. */
    public static RouteHandler cookieSession(CookieSessionOptions opts) {
        return CookieSession.create(opts);
    }

    /**
     * Progressive delay middleware — slows repeat requests instead of blocking them.
     * Equivalent to {@code express-slow-down} in Express.js.
     *
     * <pre>{@code app.use(Expressify.slowDown()); }</pre>
     */
    public static RouteHandler slowDown() {
        return SlowDown.defaults();
    }

    /** Progressive delay middleware with custom options. */
    public static RouteHandler slowDown(SlowDownOptions opts) {
        return SlowDown.configure(opts);
    }

    // Rate-limit header parsing utility

    /**
     * Parse standard rate-limit headers (IETF draft-7, X-RateLimit-*, X-Rate-Limit-*)
     * from a map of HTTP response headers.
     *
     * <p>Equivalent to the {@code ratelimit-header-parser} npm package.
     *
     * <pre>{@code
     * ParsedRateLimit info = Expressify.parseRateLimitHeaders(Map.of(
     *     "X-RateLimit-Limit",     "100",
     *     "X-RateLimit-Remaining", "42",
     *     "X-RateLimit-Reset",     "1716312000"
     * ));
     * }</pre>
     *
     * @return parsed info, or {@code null} if no recognised headers are present
     */
    public static ParsedRateLimit parseRateLimitHeaders(Map<String, String> headers) {
        return RateLimitHeaderParser.parse(headers);
    }

    /** Parse rate-limit headers with explicit options (e.g. reset type hint). */
    public static ParsedRateLimit parseRateLimitHeaders(Map<String, String> headers,
                                                         RateLimitHeaderParserOptions opts) {
        return RateLimitHeaderParser.parse(headers, opts);
    }

    /**
     * Favicon middleware — serves {@code /favicon.ico} from memory with ETag caching.
     * Equivalent to the Node.js {@code serve-favicon} package.
     *
     * <pre>{@code
     * app.use(Expressify.favicon("public/favicon.ico"));
     * }</pre>
     *
     * @param path absolute or project-relative path to the favicon file
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico}
     */
    public static RouteHandler favicon(String path) {
        return ServeFavicon.use(path);
    }

    /**
     * Favicon middleware with custom cache duration.
     *
     * <pre>{@code
     * app.use(Expressify.favicon("public/favicon.ico",
     *     FaviconOptions.builder().maxAge(2592000).build())); // 30-day cache
     * }</pre>
     *
     * @param path absolute or project-relative path to the favicon file
     * @param opts favicon options; build with {@link FaviconOptions#builder()}
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico}
     */
    public static RouteHandler favicon(String path, FaviconOptions opts) {
        return ServeFavicon.use(path, opts);
    }

    /**
     * Favicon middleware served from a raw byte array.
     *
     * <pre>{@code
     * byte[] icon = MyApp.class.getResourceAsStream("/favicon.ico").readAllBytes();
     * app.use(Expressify.favicon(icon));
     * }</pre>
     *
     * @param iconData raw bytes of the favicon file
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico}
     */
    public static RouteHandler favicon(byte[] iconData) {
        return ServeFavicon.use(iconData);
    }

    /**
     * Virtual host routing middleware — routes requests to different handlers
     * based on the {@code Host} request header. Equivalent to the Node.js {@code vhost} package.
     *
     * <p>The pattern supports {@code *} wildcards (one per hostname label).
     * Wildcard captures are available at {@code req.locals().get("vhost")} as a
     * {@link io.github.dhruvrawatdev.expressify.middleware.vhost.VHostInfo VHostInfo} object.
     *
     * <pre>{@code
     * // Exact match
     * app.use(Expressify.vhost("www.example.com", (req, res) -> res.send("Main")));
     *
     * // Wildcard subdomain — captures the matched segment
     * app.use(Expressify.vhost("*.example.com", (req, res) -> {
     *     var vhost = (VHostInfo) req.locals().get("vhost");
     *     res.send("Subdomain: " + vhost.get(0));
     * }));
     *
     * // Multiple middleware handlers for a virtual host
     * app.use(Expressify.vhost("api.example.com", authMw, Expressify.json(), apiHandler));
     * }</pre>
     *
     * @param pattern  hostname pattern — exact or wildcard (e.g. {@code "*.example.com"})
     * @param handlers one or more {@link RouteHandler}s executed when the hostname matches
     * @return a {@link RouteHandler} that performs virtual-host routing
     */
    public static RouteHandler vhost(String pattern, RouteHandler... handlers) {
        return VHost.use(pattern, handlers);
    }

    /**
     * Directory listing middleware — generates a browsable HTML/JSON/text listing
     * when a request maps to a directory. Equivalent to the Node.js {@code serve-index} package.
     *
     * <p>Use after {@link #static_(String)} so actual files are served first:
     * <pre>{@code
     * app.use(Expressify.static_("public"));
     * app.use(Expressify.serveIndex("public"));
     * }</pre>
     *
     * @param root directory to list (absolute or relative to JVM working directory)
     * @return a {@link RouteHandler} that serves directory listings
     */
    public static RouteHandler serveIndex(String root) {
        return ServeIndex.directory(root);
    }

    /**
     * Directory listing middleware with custom options.
     *
     * <pre>{@code
     * app.use(Expressify.serveIndex("uploads", ServeIndexOptions.builder()
     *     .hidden(true)
     *     .filter(name -> !name.endsWith(".tmp"))
     *     .build()));
     * }</pre>
     *
     * @param root directory to list
     * @param opts listing options; build with {@link ServeIndexOptions#builder()}
     * @return a {@link RouteHandler} that serves directory listings
     */
    public static RouteHandler serveIndex(String root, ServeIndexOptions opts) {
        return ServeIndex.directory(root, opts);
    }

    /**
     * Request timeout middleware — sends {@code 503} if the response is not started within
     * the configured window. Equivalent to the Node.js {@code connect-timeout} package.
     *
     * <p>Sets {@code req.locals().get("timedout")} to {@code Boolean.TRUE} on timeout.
     * A {@code Runnable} at {@code req.locals().get("clearTimeout")} can be called to
     * cancel the timer early.
     *
     * <pre>{@code
     * app.use(Expressify.timeout(5000));  // 5-second global timeout
     *
     * // Disable auto-503, handle it yourself
     * app.use(Expressify.timeout(3000, TimeoutOptions.builder().respond(false).build()));
     * app.use((req, res, next) -> {
     *     if (Boolean.TRUE.equals(req.locals().get("timedout"))) {
     *         res.status(503).json(Map.of("error", "Request timed out"));
     *         return;
     *     }
     *     next.run();
     * });
     * }</pre>
     *
     * @param milliseconds timeout window in milliseconds (must be {@code > 0})
     * @return a {@link RouteHandler} that enforces the request timeout
     */
    public static RouteHandler timeout(long milliseconds) {
        return Timeout.create(milliseconds);
    }

    /**
     * Request timeout middleware with custom options.
     *
     * @param milliseconds timeout window in milliseconds (must be {@code > 0})
     * @param opts         timeout options; build with {@link TimeoutOptions#builder()}
     * @return a {@link RouteHandler} that enforces the request timeout
     */
    public static RouteHandler timeout(long milliseconds, TimeoutOptions opts) {
        return Timeout.create(milliseconds, opts);
    }

    /**
     * Request timeout middleware with a human-readable duration string.
     *
     * <pre>{@code
     * app.use(Expressify.timeout("30s"));  // 30 seconds
     * app.use(Expressify.timeout("2m"));   // 2 minutes
     * }</pre>
     *
     * @param duration duration string ({@code "5s"}, {@code "2m"}, {@code "1h"}, or bare ms number)
     * @return a {@link RouteHandler} that enforces the request timeout
     */
    public static RouteHandler timeout(String duration) {
        return Timeout.create(duration);
    }

    /**
     * Development error handler — renders detailed error pages (HTML/JSON/text).
     * Equivalent to the Node.js {@code errorhandler} package.
     *
     * <p><strong>For development only.</strong> Register LAST, after all routes.
     * In production, replace with a handler that returns generic messages.
     *
     * <pre>{@code
     * // Development setup
     * app.error(Expressify.devErrorHandler());
     *
     * // Production setup
     * app.error((err, req, res, next) ->
     *     res.status(500).json(Map.of("error", "Internal Server Error")));
     * }</pre>
     *
     * @return an {@link ErrorHandler} that renders full stack traces and error details
     */
    public static ErrorHandler devErrorHandler() {
        return DevErrorHandler.create();
    }

    /**
     * Development error handler with custom options.
     *
     * <pre>{@code
     * app.error(Expressify.devErrorHandler(
     *     DevErrorHandlerOptions.builder().log(false).build()));
     * }</pre>
     *
     * @param opts handler options; build with {@link DevErrorHandlerOptions#builder()}
     * @return an {@link ErrorHandler} that renders full stack traces and error details
     */
    public static ErrorHandler devErrorHandler(DevErrorHandlerOptions opts) {
        return DevErrorHandler.create(opts);
    }

    // Proxy middleware

    /**
     * HTTP reverse-proxy middleware — forwards requests to a backend server and
     * streams the response back to the client. Equivalent to
     * {@code createProxyMiddleware()} from the Node.js {@code http-proxy-middleware} package.
     *
     * <h3>Basic usage</h3>
     * <pre>{@code
     * app.use("/api", Expressify.createProxyMiddleware(
     *     ProxyOptions.builder()
     *         .target("http://backend:3000")
     *         .changeOrigin(true)
     *         .build()));
     * }</pre>
     *
     * <h3>With path rewriting</h3>
     * <pre>{@code
     * // /api/users  →  /users on the backend
     * app.use("/api", Expressify.createProxyMiddleware(
     *     ProxyOptions.builder()
     *         .target("http://backend:3000")
     *         .changeOrigin(true)
     *         .pathRewrite("^/api", "")
     *         .build()));
     * }</pre>
     *
     * <h3>With error handling</h3>
     * <pre>{@code
     * app.use("/api", Expressify.createProxyMiddleware(
     *     ProxyOptions.builder()
     *         .target("http://backend:3000")
     *         .on(ProxyOptions.OnEvents.builder()
     *             .error((err, req, res) ->
     *                 res.status(502).json(Map.of("error", "Bad Gateway")))
     *             .build())
     *         .build()));
     * }</pre>
     *
     * @param options proxy configuration built via {@link ProxyOptions#builder()}
     * @return a {@link RouteHandler} suitable for use with {@link #use(String, RouteHandler...)}
     */
    public static RouteHandler createProxyMiddleware(ProxyOptions options) {
        return ProxyMiddleware.create(options);
    }

    // Internal helpers

    /**
     * Wrap a two-param {@link SimpleHandler} into a full three-param {@link RouteHandler}.
     * After the handler returns, the framework automatically calls {@code next.run()} if the
     * response has not yet been committed — so the handler does not need to call {@code next}
     * itself unless it wants to pass control to subsequent handlers without sending a response.
     *
     * <pre>{@code
     * RouteHandler mw = Expressify.wrap((req, res) -> {
     *     res.header("X-Custom", "value");
     *     // next() is called automatically because we didn't commit
     * });
     * }</pre>
     *
     * @param handler the two-param handler to wrap
     * @return a {@link RouteHandler} that delegates to {@code handler} and auto-advances the chain
     */
    public static RouteHandler wrap(SimpleHandler handler) {
        return (req, res, next) -> {
            handler.handle(req, res);
            if (!res.isCommitted()) next.run();
        };
    }

    // The framework only awaits completion and routes errors — it never spawns threads.
    // Threading is always controlled by the caller's executor (DB pool, HTTP client, etc.).

    private static RouteHandler asyncWrap(AsyncRouteHandler handler) {
        return (req, res, next) -> {
            boolean[] called = {false};
            NextFunction w = new NextFunction() {
                @Override public void run() throws Exception   { called[0] = true; next.run(); }
                @Override public void error(Throwable e) throws Exception { called[0] = true; next.error(e); }
            };
            java.util.concurrent.CompletableFuture<Void> f;
            try { f = handler.handle(req, res, w); } catch (Exception e) { next.error(e); return; }
            if (f == null) return;
            try {
                f.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (!called[0] && !res.isCommitted()) {
                    next.error(e.getCause() != null ? e.getCause() : e);
                    return;
                }
            }
            if (!called[0] && !res.isCommitted()) next.run();
        };
    }

    private static RouteHandler asyncWrap(AsyncSimpleHandler handler) {
        return (req, res, next) -> {
            java.util.concurrent.CompletableFuture<Void> f;
            try { f = handler.handle(req, res); } catch (Exception e) { next.error(e); return; }
            if (f == null) { if (!res.isCommitted()) next.run(); return; }
            try {
                f.join();
            } catch (java.util.concurrent.CompletionException e) {
                next.error(e.getCause() != null ? e.getCause() : e);
                return;
            }
            if (!res.isCommitted()) next.run();
        };
    }

    private static ErrorHandler asyncWrapError(AsyncErrorHandler handler) {
        return (err, req, res, next) -> {
            boolean[] called = {false};
            NextFunction w = new NextFunction() {
                @Override public void run() throws Exception   { called[0] = true; next.run(); }
                @Override public void error(Throwable e) throws Exception { called[0] = true; next.error(e); }
            };
            java.util.concurrent.CompletableFuture<Void> f;
            try { f = handler.handle(err, req, res, w); } catch (Exception e) { next.error(e); return; }
            if (f == null) return;
            try {
                f.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (!called[0] && !res.isCommitted())
                    next.error(e.getCause() != null ? e.getCause() : e);
            }
        };
    }
}
