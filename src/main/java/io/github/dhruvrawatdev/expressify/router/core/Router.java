package io.github.dhruvrawatdev.expressify.router.core;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Modular sub-router — define a group of routes and mount them on the main app at a path prefix.
 *
 * <p>A {@code Router} has the same routing API as the main {@code Expressify} class:
 * all HTTP methods, sync and async handlers, unlimited middleware, param handlers,
 * nested routers, error handlers, and fluent {@code route()} chaining.
 * When mounted, the prefix is stripped from the request path before the router's handlers run.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * // users.java
 * Router usersRouter = new Router();
 *
 * usersRouter.use(authMiddleware);          // applied to all routes in this router
 *
 * usersRouter.get("/",    (req, res) -> res.json(getAllUsers()));
 * usersRouter.get("/:id", (req, res) -> res.json(getUser(req.param("id"))));
 * usersRouter.post("/",   (req, res) -> res.status(201).json(createUser(req.body())));
 * usersRouter.put("/:id", (req, res) -> res.json(updateUser(req.param("id"), req.body())));
 * usersRouter.delete("/:id", (req, res) -> { deleteUser(req.param("id")); res.status(204).send(); });
 *
 * // main.java
 * app.use("/users", usersRouter);
 * // → GET /users       handled by usersRouter's "/"
 * // → GET /users/42    handled by usersRouter's "/:id"
 * }</pre>
 *
 * <h2>Nested routers</h2>
 * <pre>{@code
 * Router postsRouter = new Router();
 * postsRouter.get("/", (req, res) -> res.json(getPosts(req.param("userId"))));
 *
 * usersRouter.use("/:userId/posts", postsRouter);
 * app.use("/users", usersRouter);
 * // → GET /users/42/posts
 * }</pre>
 *
 * @see io.github.dhruvrawatdev.expressify.Expressify Expressify for the root application class
 * @see RouteBuilder for fluent multi-method chaining via {@code router.route(path)}
 */
public class Router implements RouteEntry.RouterRef {

    protected final RouteRegistry registry = new RouteRegistry();

    // HTTP Methods

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
     * Register a GET route handler.
     * Path is relative to the mount prefix set in {@code app.use(prefix, router)}.
     *
     * <pre>{@code
     * router.get("/",    (req, res) -> res.json(getAll()));
     * router.get("/:id", (req, res) -> res.json(get(req.param("id"))));
     *
     * // Middleware chain
     * router.get("/:id", authMw, (req, res) -> res.json(get(req.param("id"))));
     *
     * // Unlimited middleware — array form
     * router.get("/:id", new RouteHandler[]{auth, log}, (req, res) -> res.json(get(req.param("id"))));
     * }</pre>
     *
     * @param path     route path (relative to this router's mount prefix);
     *                 supports {@code :param} placeholders and wildcards
     * @param handlers one or more {@link RouteHandler}s executed in order
     * @return this router for chaining
     */
    public Router get(String path, RouteHandler... handlers) {
        registry.addRoute("GET", path, Arrays.asList(handlers));
        return this;
    }
    /** GET — two-param {@link SimpleHandler} variant; framework auto-calls {@code next()} on return. */
    public Router get(String path, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(wrap(handler)));
        return this;
    }
    /** GET — one middleware + terminal {@link SimpleHandler}. */
    public Router get(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** GET — two middleware + terminal {@link SimpleHandler}. */
    public Router get(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("GET", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** GET — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router get(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("GET", path, buildList(middlewares, handler));
        return this;
    }
    /** GET — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router get(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("GET", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a POST route handler.
     *
     * <pre>{@code
     * router.post("/", (req, res) -> res.status(201).json(create(req.body())));
     * }</pre>
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router post(String path, RouteHandler... handlers) {
        registry.addRoute("POST", path, Arrays.asList(handlers));
        return this;
    }
    /** POST — two-param {@link SimpleHandler}. */
    public Router post(String path, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(wrap(handler)));
        return this;
    }
    /** POST — one middleware + terminal {@link SimpleHandler}. */
    public Router post(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** POST — two middleware + terminal {@link SimpleHandler}. */
    public Router post(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("POST", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** POST — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router post(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("POST", path, buildList(middlewares, handler));
        return this;
    }
    /** POST — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router post(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("POST", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PUT route handler (full resource replacement).
     *
     * <pre>{@code router.put("/:id", (req, res) -> res.json(replace(req.param("id"), req.body()))); }</pre>
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router put(String path, RouteHandler... handlers) {
        registry.addRoute("PUT", path, Arrays.asList(handlers));
        return this;
    }
    /** PUT — two-param {@link SimpleHandler}. */
    public Router put(String path, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(wrap(handler)));
        return this;
    }
    /** PUT — one middleware + terminal {@link SimpleHandler}. */
    public Router put(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** PUT — two middleware + terminal {@link SimpleHandler}. */
    public Router put(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("PUT", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** PUT — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router put(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("PUT", path, buildList(middlewares, handler));
        return this;
    }
    /** PUT — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router put(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("PUT", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a DELETE route handler.
     *
     * <pre>{@code router.delete("/:id", (req, res) -> { delete(req.param("id")); res.status(204).send(); }); }</pre>
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router delete(String path, RouteHandler... handlers) {
        registry.addRoute("DELETE", path, Arrays.asList(handlers));
        return this;
    }
    /** DELETE — two-param {@link SimpleHandler}. */
    public Router delete(String path, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(wrap(handler)));
        return this;
    }
    /** DELETE — one middleware + terminal {@link SimpleHandler}. */
    public Router delete(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** DELETE — two middleware + terminal {@link SimpleHandler}. */
    public Router delete(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("DELETE", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** DELETE — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router delete(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("DELETE", path, buildList(middlewares, handler));
        return this;
    }
    /** DELETE — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router delete(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("DELETE", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a PATCH route handler (partial update).
     *
     * <pre>{@code router.patch("/:id", (req, res) -> res.json(partialUpdate(req.param("id"), req.body()))); }</pre>
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router patch(String path, RouteHandler... handlers) {
        registry.addRoute("PATCH", path, Arrays.asList(handlers));
        return this;
    }
    /** PATCH — two-param {@link SimpleHandler}. */
    public Router patch(String path, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(wrap(handler)));
        return this;
    }
    /** PATCH — one middleware + terminal {@link SimpleHandler}. */
    public Router patch(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** PATCH — two middleware + terminal {@link SimpleHandler}. */
    public Router patch(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("PATCH", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** PATCH — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router patch(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("PATCH", path, buildList(middlewares, handler));
        return this;
    }
    /** PATCH — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router patch(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("PATCH", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register an OPTIONS route handler.
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router options(String path, RouteHandler... handlers) {
        registry.addRoute("OPTIONS", path, Arrays.asList(handlers));
        return this;
    }
    /** OPTIONS — two-param {@link SimpleHandler}. */
    public Router options(String path, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(wrap(handler)));
        return this;
    }
    /** OPTIONS — one middleware + terminal {@link SimpleHandler}. */
    public Router options(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** OPTIONS — two middleware + terminal {@link SimpleHandler}. */
    public Router options(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** OPTIONS — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router options(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, buildList(middlewares, handler));
        return this;
    }
    /** OPTIONS — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router options(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("OPTIONS", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a HEAD route handler.
     *
     * @param path     route path relative to this router's mount prefix
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router head(String path, RouteHandler... handlers) {
        registry.addRoute("HEAD", path, Arrays.asList(handlers));
        return this;
    }
    /** HEAD — two-param {@link SimpleHandler}. */
    public Router head(String path, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(wrap(handler)));
        return this;
    }
    /** HEAD — one middleware + terminal {@link SimpleHandler}. */
    public Router head(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** HEAD — two middleware + terminal {@link SimpleHandler}. */
    public Router head(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("HEAD", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** HEAD — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router head(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("HEAD", path, buildList(middlewares, handler));
        return this;
    }
    /** HEAD — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router head(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("HEAD", path, buildList(middlewares, handler));
        return this;
    }

    /**
     * Register a handler that matches any HTTP method at the given path.
     * Useful for path-scoped middleware that runs regardless of verb.
     *
     * <pre>{@code
     * router.all("/*", (req, res, next) -> {
     *     log(req.method() + " " + req.path());
     *     next.run();
     * });
     * }</pre>
     *
     * @param path     route path
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router all(String path, RouteHandler... handlers) {
        registry.addRoute("ALL", path, Arrays.asList(handlers));
        return this;
    }
    /** ALL — two-param {@link SimpleHandler}. */
    public Router all(String path, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(wrap(handler)));
        return this;
    }
    /** ALL — one middleware + terminal {@link SimpleHandler}. */
    public Router all(String path, RouteHandler mw, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(mw, wrap(handler)));
        return this;
    }
    /** ALL — two middleware + terminal {@link SimpleHandler}. */
    public Router all(String path, RouteHandler mw1, RouteHandler mw2, SimpleHandler handler) {
        registry.addRoute("ALL", path, List.of(mw1, mw2, wrap(handler)));
        return this;
    }
    /** ALL — unlimited middleware (array) + terminal {@link SimpleHandler}. */
    public Router all(String path, RouteHandler[] middlewares, SimpleHandler handler) {
        registry.addRoute("ALL", path, buildList(middlewares, handler));
        return this;
    }
    /** ALL — unlimited middleware (list) + terminal {@link SimpleHandler}. */
    public Router all(String path, List<RouteHandler> middlewares, SimpleHandler handler) {
        registry.addRoute("ALL", path, buildList(middlewares, handler));
        return this;
    }

    // Async HTTP Methods

    /**
     * Register an async GET handler via explicit cast or typed variable.
     * Future rejection automatically routes to this router's error handler.
     *
     * <pre>{@code
     * router.get("/:id", (AsyncRouteHandler)(req, res, next) ->
     *     db.findAsync(req.param("id")).thenAccept(user -> res.json(user))
     * );
     * }</pre>
     *
     * @param path route path relative to this router's mount prefix
     * @param h    async handler returning {@code CompletableFuture<Void>}
     * @return this router for chaining
     */
    /**
     * Register an async GET handler.
     *
     * @param path route path relative to this router's mount prefix
     * @param h    async handler returning {@code CompletableFuture<Void>}
     * @return this router for chaining
     */
    public Router getAsync(String path, AsyncRouteHandler h) { registry.addRoute("GET",     path, Arrays.asList(async(h))); return this; }
    /** Async POST — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router postAsync(String path, AsyncRouteHandler h) { registry.addRoute("POST",    path, Arrays.asList(async(h))); return this; }
    /** Async PUT — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router putAsync(String path, AsyncRouteHandler h) { registry.addRoute("PUT",     path, Arrays.asList(async(h))); return this; }
    /** Async DELETE — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router deleteAsync(String path, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, Arrays.asList(async(h))); return this; }
    /** Async PATCH — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router patchAsync(String path, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, Arrays.asList(async(h))); return this; }
    /** Async OPTIONS — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router optionsAsync(String path, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, Arrays.asList(async(h))); return this; }
    /** Async HEAD — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router headAsync(String path, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, Arrays.asList(async(h))); return this; }
    /** Async ALL — see {@link #getAsync(String, AsyncRouteHandler)}. */
    public Router allAsync(String path, AsyncRouteHandler h) { registry.addRoute("ALL",     path, Arrays.asList(async(h))); return this; }

    /** Async GET, no-next — use {@code getAsync()} for unambiguous lambda registration. */
    public Router getAsync(String path, AsyncSimpleHandler h) { registry.addRoute("GET",     path, Arrays.asList(async(h))); return this; }
    /** Async POST, no-next. */
    public Router postAsync(String path, AsyncSimpleHandler h) { registry.addRoute("POST",    path, Arrays.asList(async(h))); return this; }
    /** Async PUT, no-next. */
    public Router putAsync(String path, AsyncSimpleHandler h) { registry.addRoute("PUT",     path, Arrays.asList(async(h))); return this; }
    /** Async DELETE, no-next. */
    public Router deleteAsync(String path, AsyncSimpleHandler h) { registry.addRoute("DELETE",  path, Arrays.asList(async(h))); return this; }
    /** Async PATCH, no-next. */
    public Router patchAsync(String path, AsyncSimpleHandler h) { registry.addRoute("PATCH",   path, Arrays.asList(async(h))); return this; }
    /** Async OPTIONS, no-next. */
    public Router optionsAsync(String path, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, Arrays.asList(async(h))); return this; }
    /** Async HEAD, no-next. */
    public Router headAsync(String path, AsyncSimpleHandler h) { registry.addRoute("HEAD",    path, Arrays.asList(async(h))); return this; }
    /** Async ALL, no-next. */
    public Router allAsync(String path, AsyncSimpleHandler h) { registry.addRoute("ALL",     path, Arrays.asList(async(h))); return this; }

    // Async + unlimited sync middleware — array form

    /**
     * Register an async GET route with unlimited sync middleware (array) + async terminal handler.
     *
     * <pre>{@code
     * router.getAsync("/:id",
     *     new RouteHandler[]{authMw, logMw},
     *     (req, res, next) -> db.findAsync(req.param("id")).thenAccept(u -> res.json(u))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered array of sync middleware
     * @param h    async terminal handler
     * @return this router for chaining
     */
    public Router getAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("GET",     path, buildListR(mws, async(h))); return this; }
    /** Async POST + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router postAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("POST",    path, buildListR(mws, async(h))); return this; }
    /** Async PUT + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router putAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PUT",     path, buildListR(mws, async(h))); return this; }
    /** Async DELETE + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router deleteAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, buildListR(mws, async(h))); return this; }
    /** Async PATCH + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router patchAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, buildListR(mws, async(h))); return this; }
    /** Async OPTIONS + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router optionsAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, async(h))); return this; }
    /** Async HEAD + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router headAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, buildListR(mws, async(h))); return this; }
    /** Async ALL + middleware array — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router allAsync(String path, RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("ALL",     path, buildListR(mws, async(h))); return this; }

    /** Async GET + middleware array, no-next terminal — see {@link #getAsync(String, RouteHandler[], AsyncRouteHandler)}. */
    public Router getAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("GET",     path, buildListR(mws, async(h))); return this; }
    /** Async POST + middleware array, no-next terminal. */
    public Router postAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("POST",    path, buildListR(mws, async(h))); return this; }
    /** Async PUT + middleware array, no-next terminal. */
    public Router putAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PUT",     path, buildListR(mws, async(h))); return this; }
    /** Async DELETE + middleware array, no-next terminal. */
    public Router deleteAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("DELETE",  path, buildListR(mws, async(h))); return this; }
    /** Async PATCH + middleware array, no-next terminal. */
    public Router patchAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PATCH",   path, buildListR(mws, async(h))); return this; }
    /** Async OPTIONS + middleware array, no-next terminal. */
    public Router optionsAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, async(h))); return this; }
    /** Async HEAD + middleware array, no-next terminal. */
    public Router headAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("HEAD",    path, buildListR(mws, async(h))); return this; }
    /** Async ALL + middleware array, no-next terminal. */
    public Router allAsync(String path, RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("ALL",     path, buildListR(mws, async(h))); return this; }

    // Async + unlimited sync middleware — List form

    /**
     * Register an async GET route with unlimited sync middleware (list) + async terminal handler.
     *
     * <pre>{@code
     * router.getAsync("/:id",
     *     List.of(authMw, logMw),
     *     (req, res, next) -> db.findAsync(req.param("id")).thenAccept(u -> res.json(u))
     * );
     * }</pre>
     *
     * @param path route path
     * @param mws  ordered list of sync middleware
     * @param h    async terminal handler
     * @return this router for chaining
     */
    public Router getAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("GET",     path, buildListR(mws, async(h))); return this; }
    /** Async POST + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router postAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("POST",    path, buildListR(mws, async(h))); return this; }
    /** Async PUT + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router putAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PUT",     path, buildListR(mws, async(h))); return this; }
    /** Async DELETE + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router deleteAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, buildListR(mws, async(h))); return this; }
    /** Async PATCH + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router patchAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, buildListR(mws, async(h))); return this; }
    /** Async OPTIONS + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router optionsAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, async(h))); return this; }
    /** Async HEAD + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router headAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, buildListR(mws, async(h))); return this; }
    /** Async ALL + middleware list — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router allAsync(String path, List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("ALL",     path, buildListR(mws, async(h))); return this; }

    /** Async GET + middleware list, no-next terminal — see {@link #getAsync(String, List, AsyncRouteHandler)}. */
    public Router getAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("GET",     path, buildListR(mws, async(h))); return this; }
    /** Async POST + middleware list, no-next terminal. */
    public Router postAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("POST",    path, buildListR(mws, async(h))); return this; }
    /** Async PUT + middleware list, no-next terminal. */
    public Router putAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PUT",     path, buildListR(mws, async(h))); return this; }
    /** Async DELETE + middleware list, no-next terminal. */
    public Router deleteAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("DELETE",  path, buildListR(mws, async(h))); return this; }
    /** Async PATCH + middleware list, no-next terminal. */
    public Router patchAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PATCH",   path, buildListR(mws, async(h))); return this; }
    /** Async OPTIONS + middleware list, no-next terminal. */
    public Router optionsAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListR(mws, async(h))); return this; }
    /** Async HEAD + middleware list, no-next terminal. */
    public Router headAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("HEAD",    path, buildListR(mws, async(h))); return this; }
    /** Async ALL + middleware list, no-next terminal. */
    public Router allAsync(String path, List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("ALL",     path, buildListR(mws, async(h))); return this; }

    /**
     * Return a fluent {@link RouteBuilder} for chaining multiple HTTP methods on the same path.
     *
     * <pre>{@code
     * router.route("/:id")
     *       .get(getHandler)
     *       .put(updateHandler)
     *       .delete(deleteHandler);
     * }</pre>
     *
     * @param path the route path shared by all chained handlers
     * @return a new {@link RouteBuilder} scoped to {@code path}
     */
    public RouteBuilder route(String path) {
        return new RouteBuilder(path, registry);
    }

    // Middleware

    /**
     * Apply one or more middleware handlers to all routes in this router.
     * Middleware is executed in registration order before any matching route handler.
     *
     * <pre>{@code
     * router.use(authMiddleware, logMiddleware);
     * }</pre>
     *
     * @param handlers one or more {@link RouteHandler}s; each must call {@code next.run()} to continue
     * @return this router for chaining
     */
    public Router use(RouteHandler... handlers) {
        registry.addMiddleware(null, Arrays.asList(handlers));
        return this;
    }

    /**
     * Apply a two-param {@link SimpleHandler} as middleware for all routes in this router.
     *
     * @param handler middleware handler; framework auto-calls {@code next()} on return
     * @return this router for chaining
     */
    public Router use(SimpleHandler handler) {
        registry.addMiddleware(null, Arrays.asList(wrap(handler)));
        return this;
    }

    /**
     * Apply middleware only to routes under the given path prefix within this router.
     *
     * @param path     path prefix relative to this router
     * @param handlers one or more {@link RouteHandler}s
     * @return this router for chaining
     */
    public Router use(String path, RouteHandler... handlers) {
        registry.addMiddleware(path, Arrays.asList(handlers));
        return this;
    }

    /**
     * Apply a two-param {@link SimpleHandler} under a path prefix within this router.
     *
     * @param path    path prefix
     * @param handler middleware handler
     * @return this router for chaining
     */
    public Router use(String path, SimpleHandler handler) {
        registry.addMiddleware(path, Arrays.asList(wrap(handler)));
        return this;
    }

    /**
     * Mount a nested {@link Router} at the given path prefix.
     * The prefix is stripped before entering the nested router's handler chain.
     *
     * <pre>{@code
     * Router postsRouter = new Router();
     * postsRouter.get("/", (req, res) -> res.json(getPosts()));
     *
     * usersRouter.use("/:userId/posts", postsRouter);
     * }</pre>
     *
     * @param path         the sub-prefix within this router's scope
     * @param nestedRouter the router to mount
     * @return this router for chaining
     */
    public Router use(String path, Router nestedRouter) {
        registry.addRouter(path, nestedRouter);
        return this;
    }

    /**
     * Apply async middleware to all routes in this router.
     *
     * @param handler async handler
     * @return this router for chaining
     */
    public Router useAsync(AsyncRouteHandler handler) {
        registry.addMiddleware(null, Arrays.asList(async(handler)));
        return this;
    }

    /**
     * Named-sugar alias for {@link #use(AsyncSimpleHandler)} — apply async middleware globally (no-next).
     *
     * @param handler async two-param handler
     * @return this router for chaining
     */
    public Router useAsync(AsyncSimpleHandler handler) {
        registry.addMiddleware(null, Arrays.asList(async(handler)));
        return this;
    }

    /**
     * Apply async middleware to routes under the given path prefix.
     *
     * @param path    path prefix
     * @param handler async handler
     * @return this router for chaining
     */
    public Router useAsync(String path, AsyncRouteHandler handler) {
        registry.addMiddleware(path, Arrays.asList(async(handler)));
        return this;
    }

    /**
     * Named-sugar alias for {@link #use(String, AsyncSimpleHandler)}.
     *
     * @param path    path prefix
     * @param handler async two-param handler
     * @return this router for chaining
     */
    public Router useAsync(String path, AsyncSimpleHandler handler) {
        registry.addMiddleware(path, Arrays.asList(async(handler)));
        return this;
    }

    /**
     * Register an error handler scoped to this router.
     * Errors thrown within this router's middleware or routes are caught here
     * before propagating to the parent app's error handler.
     *
     * <pre>{@code
     * router.error((err, req, res, next) ->
     *     res.status(500).json(Map.of("error", err.getMessage()))
     * );
     * }</pre>
     *
     * @param handler four-param error handler: {@code (err, req, res, next) -> void}
     * @return this router for chaining
     */
    public Router error(ErrorHandler handler) {
        registry.addErrorHandler(handler);
        return this;
    }

    /**
     * Register an async error handler for this router via explicit cast or typed variable.
     *
     * <pre>{@code
     * router.error((AsyncErrorHandler)(err, req, res, next) ->
     *     auditLog.writeAsync(err).thenRun(() -> res.status(500).send("Error"))
     * );
     * }</pre>
     *
     * @param handler async error handler returning {@code CompletableFuture<Void>}
     * @return this router for chaining
     */
    /**
     * Register an async error handler via named sugar.
     *
     * @param handler async error handler returning {@code CompletableFuture<Void>}
     * @return this router for chaining
     */
    public Router errorAsync(AsyncErrorHandler handler) {
        return error(asyncError(handler));
    }

    /**
     * Register a param handler that runs before any route in this router that contains the named parameter.
     * Useful for loading a resource by ID once and sharing it via {@code req.locals()}.
     *
     * <pre>{@code
     * router.param("id", (req, res, next, id) -> {
     *     User user = db.findUser(id);
     *     if (user == null) { res.status(404).send("Not found"); return; }
     *     req.locals().put("user", user);
     *     next.run();
     * });
     * // Now every "/:id" route can access req.locals().get("user")
     * }</pre>
     *
     * @param name    the route parameter name (without the leading colon)
     * @param handler called with {@code (req, res, next, value)} before the route handler runs
     * @return this router for chaining
     */
    public Router param(String name, ParamHandler handler) {
        registry.addParamHandler(name, handler);
        return this;
    }

    /**
     * Async variant of {@link #param(String, ParamHandler)} — use when param resolution involves async I/O.
     *
     * <pre>{@code
     * router.param("id", (AsyncParamHandler)(req, res, next, id) ->
     *     db.findUserAsync(id).thenAccept(user -> {
     *         req.locals().put("user", user);
     *         try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
     *     })
     * );
     * }</pre>
     *
     * @param name    the route parameter name (without the leading colon)
     * @param handler async param handler
     * @return this router for chaining
     */
    public Router param(String name, AsyncParamHandler handler) {
        registry.addParamHandler(name, handler);
        return this;
    }

    //  RouterRef

    @Override
    public List<RouteEntry> getEntries() {
        return registry.getEntries();
    }

    @Override
    public Map<String, List<ParamHandler>> getParamHandlers() {
        return registry.getParamHandlers();
    }

    // Internal async helpers (package-private — threading controlled by caller)

    /**
     * Wrap an {@link AsyncRouteHandler} into a synchronous {@link RouteHandler}.
     * The future is joined on the calling (Undertow worker) thread; threading is always
     * controlled by the caller's executor (e.g. a DB connection pool or HTTP client).
     * Future rejection is automatically forwarded to {@code next.error(cause)}.
     *
     * @param handler the async handler to wrap
     * @return a {@link RouteHandler} that drives the future and propagates errors
     */
    static RouteHandler async(AsyncRouteHandler handler) {
        return (req, res, next) -> {
            boolean[] called = {false};
            NextFunction wrapped = new NextFunction() {
                @Override public void run() throws Exception   { called[0] = true; next.run(); }
                @Override public void error(Throwable e) throws Exception { called[0] = true; next.error(e); }
            };
            java.util.concurrent.CompletableFuture<Void> future;
            try {
                future = handler.handle(req, res, wrapped);
            } catch (Exception e) {
                next.error(e);
                return;
            }
            if (future == null) return;
            try {
                future.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (!called[0] && !res.isCommitted()) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    next.error(cause);
                    return;
                }
            }
            if (!called[0] && !res.isCommitted()) next.run();
        };
    }

    static RouteHandler async(AsyncSimpleHandler handler) {
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

    static ErrorHandler asyncError(AsyncErrorHandler handler) {
        return (err, req, res, next) -> {
            boolean[] called = {false};
            NextFunction wrapped = new NextFunction() {
                @Override public void run() throws Exception   { called[0] = true; next.run(); }
                @Override public void error(Throwable e) throws Exception { called[0] = true; next.error(e); }
            };
            java.util.concurrent.CompletableFuture<Void> future;
            try {
                future = handler.handle(err, req, res, wrapped);
            } catch (Exception e) {
                next.error(e);
                return;
            }
            if (future == null) return;
            try {
                future.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (!called[0] && !res.isCommitted()) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    next.error(cause);
                }
            }
        };
    }

    // Helper

    /**
     * Wrap a two-param {@link SimpleHandler} into a three-param {@link RouteHandler}.
     * Automatically calls {@code next.run()} after the handler returns if the response
     * has not yet been committed, allowing use as route-scoped middleware.
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
}
