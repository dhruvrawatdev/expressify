package io.github.dhruvrawatdev.expressify.router.core;

import io.github.dhruvrawatdev.expressify.router.handler.AsyncRouteHandler;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncSimpleHandler;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.router.handler.SimpleHandler;
import io.github.dhruvrawatdev.expressify.router.registry.RouteRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent route-chaining builder returned by {@code app.route(path)} and {@code router.route(path)}.
 *
 * <p>Avoids repeating the same path string when wiring multiple HTTP verbs.
 * Every method returns {@code this} so calls can be chained indefinitely.
 *
 * <pre>{@code
 * app.route("/api/users")
 *    .get((req, res) -> res.json(users))
 *    .post((req, res) -> { users.add(req.body()); res.status(201).json(req.body()); });
 *
 * app.route("/api/users/:id")
 *    .get(getHandler)
 *    .put(updateHandler)
 *    .patch(patchHandler)
 *    .delete(deleteHandler);
 * }</pre>
 *
 * <p>All handler types are supported on every verb:
 * <ul>
 *   <li>{@link RouteHandler} {@code (req, res, next) -> void}</li>
 *   <li>{@link SimpleHandler} {@code (req, res) -> void} — auto-advances {@code next()}</li>
 *   <li>{@link AsyncRouteHandler} / {@link AsyncSimpleHandler} — via named sugar or explicit cast</li>
 *   <li>Array/list middleware prefix + terminal handler — unlimited middleware</li>
 * </ul>
 *
 * @see io.github.dhruvrawatdev.expressify.Expressify#route(String) Expressify.route(path)
 * @see Router#route(String) Router.route(path)
 */
public class RouteBuilder {

    private final String path;
    private final RouteRegistry registry;

    /**
     * Construct a builder for the given path, backed by the supplied registry.
     * Not called directly — use {@code app.route(path)} or {@code router.route(path)}.
     *
     * @param path     the route path (shared by all handlers added via this builder)
     * @param registry the registry to which routes are added
     */
    public RouteBuilder(String path, RouteRegistry registry) {
        this.path = path;
        this.registry = registry;
    }

    // RouteHandler varargs

    /**
     * Register a GET handler using varargs — accepts any number of {@link RouteHandler}s.
     *
     * <pre>{@code
     * app.route("/users")
     *    .get(authMw, logMw, (req, res, next) -> res.json(users));
     * }</pre>
     *
     * @param handlers one or more handlers; each (except the last) must call {@code next.run()}
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder get(RouteHandler... handlers) {
        registry.addRoute("GET", path, Arrays.asList(handlers));
        return this;
    }

    /** Register a POST handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder post(RouteHandler... handlers) {
        registry.addRoute("POST", path, Arrays.asList(handlers));
        return this;
    }

    /** Register a PUT handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder put(RouteHandler... handlers) {
        registry.addRoute("PUT", path, Arrays.asList(handlers));
        return this;
    }

    /** Register a DELETE handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder delete(RouteHandler... handlers) {
        registry.addRoute("DELETE", path, Arrays.asList(handlers));
        return this;
    }

    /** Register a PATCH handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder patch(RouteHandler... handlers) {
        registry.addRoute("PATCH", path, Arrays.asList(handlers));
        return this;
    }

    /** Register an OPTIONS handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder options(RouteHandler... handlers) {
        registry.addRoute("OPTIONS", path, Arrays.asList(handlers));
        return this;
    }

    /** Register a HEAD handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder head(RouteHandler... handlers) {
        registry.addRoute("HEAD", path, Arrays.asList(handlers));
        return this;
    }

    /** Register an all-methods handler via varargs — see {@link #get(RouteHandler...)}. */
    public RouteBuilder all(RouteHandler... handlers) {
        registry.addRoute("ALL", path, Arrays.asList(handlers));
        return this;
    }

    // SimpleHandler (two-param, no next)

    /**
     * Register a GET handler via the two-param {@link SimpleHandler}.
     * The framework calls {@code next()} automatically if the response is not committed on return.
     *
     * <pre>{@code
     * app.route("/users")
     *    .get((req, res) -> res.json(users))
     *    .post((req, res) -> res.status(201).json(create(req.body())));
     * }</pre>
     *
     * @param h terminal handler (no {@code next} parameter needed)
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder get(SimpleHandler h) { return get(wrap(h)); }
    /** POST — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder post(SimpleHandler h) { return post(wrap(h)); }
    /** PUT — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder put(SimpleHandler h) { return put(wrap(h)); }
    /** DELETE — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder delete(SimpleHandler h) { return delete(wrap(h)); }
    /** PATCH — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder patch(SimpleHandler h) { return patch(wrap(h)); }
    /** OPTIONS — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder options(SimpleHandler h) { return options(wrap(h)); }
    /** HEAD — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder head(SimpleHandler h) { return head(wrap(h)); }
    /** ALL — two-param {@link SimpleHandler}; see {@link #get(SimpleHandler)}. */
    public RouteBuilder all(SimpleHandler h)  { return all(wrap(h)); }


    private List<RouteHandler> buildList(RouteHandler[] mws, SimpleHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.length + 1);
        list.addAll(Arrays.asList(mws));
        list.add(wrap(terminal));
        return list;
    }

    private List<RouteHandler> buildList(List<RouteHandler> mws, SimpleHandler terminal) {
        List<RouteHandler> list = new ArrayList<>(mws.size() + 1);
        list.addAll(mws);
        list.add(wrap(terminal));
        return list;
    }

    /**
     * Register a GET route with unlimited middleware (array form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.route("/admin")
     *    .get(new RouteHandler[]{auth, log, audit}, (req, res) -> res.json(data));
     * }</pre>
     *
     * @param mws array of middleware handlers; each must call {@code next.run()}
     * @param h   terminal handler
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder get(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("GET",     path, buildList(mws, h)); return this; }
    /** POST — middleware array + terminal {@link SimpleHandler}; see {@link #get(RouteHandler[], SimpleHandler)}. */
    public RouteBuilder post(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("POST",    path, buildList(mws, h)); return this; }
    /** PUT — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder put(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("PUT",     path, buildList(mws, h)); return this; }
    /** DELETE — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder delete(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("DELETE",  path, buildList(mws, h)); return this; }
    /** PATCH — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder patch(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("PATCH",   path, buildList(mws, h)); return this; }
    /** OPTIONS — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder options(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("OPTIONS", path, buildList(mws, h)); return this; }
    /** HEAD — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder head(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("HEAD",    path, buildList(mws, h)); return this; }
    /** ALL — middleware array + terminal {@link SimpleHandler}. */
    public RouteBuilder all(RouteHandler[] mws, SimpleHandler h) { registry.addRoute("ALL",     path, buildList(mws, h)); return this; }

    /**
     * Register a GET route with unlimited middleware (list form) and a terminal {@link SimpleHandler}.
     *
     * <pre>{@code
     * app.route("/admin")
     *    .get(List.of(auth, log, audit), (req, res) -> res.json(data));
     * }</pre>
     *
     * @param mws ordered list of middleware handlers; each must call {@code next.run()}
     * @param h   terminal handler
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder get(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("GET",     path, buildList(mws, h)); return this; }
    /** POST — middleware list + terminal {@link SimpleHandler}; see {@link #get(List, SimpleHandler)}. */
    public RouteBuilder post(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("POST",    path, buildList(mws, h)); return this; }
    /** PUT — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder put(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("PUT",     path, buildList(mws, h)); return this; }
    /** DELETE — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder delete(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("DELETE",  path, buildList(mws, h)); return this; }
    /** PATCH — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder patch(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("PATCH",   path, buildList(mws, h)); return this; }
    /** OPTIONS — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder options(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("OPTIONS", path, buildList(mws, h)); return this; }
    /** HEAD — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder head(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("HEAD",    path, buildList(mws, h)); return this; }
    /** ALL — middleware list + terminal {@link SimpleHandler}. */
    public RouteBuilder all(List<RouteHandler> mws, SimpleHandler h) { registry.addRoute("ALL",     path, buildList(mws, h)); return this; }

    /**
     * Register a GET route with unlimited sync middleware (array) and an async terminal handler.
     *
     * <pre>{@code
     * app.route("/users")
     *    .getAsync(new RouteHandler[]{auth, log},
     *              (req, res, next) -> db.findAllAsync().thenAccept(u -> res.json(u)));
     * }</pre>
     *
     * @param mws ordered array of sync middleware
     * @param h   async terminal handler; future rejection routes to error handler automatically
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("GET",     path, buildListA(mws, h)); return this; }
    /** POST — middleware array + async terminal; see {@link #getAsync(RouteHandler[], AsyncRouteHandler)}. */
    public RouteBuilder postAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("POST",    path, buildListA(mws, h)); return this; }
    /** PUT — middleware array + async terminal. */
    public RouteBuilder putAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PUT",     path, buildListA(mws, h)); return this; }
    /** DELETE — middleware array + async terminal. */
    public RouteBuilder deleteAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, buildListA(mws, h)); return this; }
    /** PATCH — middleware array + async terminal. */
    public RouteBuilder patchAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, buildListA(mws, h)); return this; }
    /** OPTIONS — middleware array + async terminal. */
    public RouteBuilder optionsAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListA(mws, h)); return this; }
    /** HEAD — middleware array + async terminal. */
    public RouteBuilder headAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, buildListA(mws, h)); return this; }
    /** ALL — middleware array + async terminal. */
    public RouteBuilder allAsync(RouteHandler[] mws, AsyncRouteHandler h) { registry.addRoute("ALL",     path, buildListA(mws, h)); return this; }

    /**
     * Register a GET route with unlimited sync middleware (array) and a no-next async terminal handler.
     *
     * @param mws ordered array of sync middleware
     * @param h   async two-param terminal handler (no {@code next})
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("GET",     path, buildListA(mws, h)); return this; }
    /** POST — middleware array + async no-next terminal. */
    public RouteBuilder postAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("POST",    path, buildListA(mws, h)); return this; }
    /** PUT — middleware array + async no-next terminal. */
    public RouteBuilder putAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PUT",     path, buildListA(mws, h)); return this; }
    /** DELETE — middleware array + async no-next terminal. */
    public RouteBuilder deleteAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("DELETE",  path, buildListA(mws, h)); return this; }
    /** PATCH — middleware array + async no-next terminal. */
    public RouteBuilder patchAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("PATCH",   path, buildListA(mws, h)); return this; }
    /** OPTIONS — middleware array + async no-next terminal. */
    public RouteBuilder optionsAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListA(mws, h)); return this; }
    /** HEAD — middleware array + async no-next terminal. */
    public RouteBuilder headAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("HEAD",    path, buildListA(mws, h)); return this; }
    /** ALL — middleware array + async no-next terminal. */
    public RouteBuilder allAsync(RouteHandler[] mws, AsyncSimpleHandler h) { registry.addRoute("ALL",     path, buildListA(mws, h)); return this; }

    /**
     * Register a GET route with unlimited sync middleware (list) and an async terminal handler.
     *
     * <pre>{@code
     * app.route("/users")
     *    .getAsync(List.of(auth, log),
     *              (req, res, next) -> db.findAllAsync().thenAccept(u -> res.json(u)));
     * }</pre>
     *
     * @param mws ordered list of sync middleware
     * @param h   async terminal handler
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("GET",     path, buildListA(mws, h)); return this; }
    /** POST — middleware list + async terminal; see {@link #getAsync(List, AsyncRouteHandler)}. */
    public RouteBuilder postAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("POST",    path, buildListA(mws, h)); return this; }
    /** PUT — middleware list + async terminal. */
    public RouteBuilder putAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PUT",     path, buildListA(mws, h)); return this; }
    /** DELETE — middleware list + async terminal. */
    public RouteBuilder deleteAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("DELETE",  path, buildListA(mws, h)); return this; }
    /** PATCH — middleware list + async terminal. */
    public RouteBuilder patchAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("PATCH",   path, buildListA(mws, h)); return this; }
    /** OPTIONS — middleware list + async terminal. */
    public RouteBuilder optionsAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("OPTIONS", path, buildListA(mws, h)); return this; }
    /** HEAD — middleware list + async terminal. */
    public RouteBuilder headAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("HEAD",    path, buildListA(mws, h)); return this; }
    /** ALL — middleware list + async terminal. */
    public RouteBuilder allAsync(List<RouteHandler> mws, AsyncRouteHandler h) { registry.addRoute("ALL",     path, buildListA(mws, h)); return this; }

    /**
     * Register a GET route with unlimited sync middleware (list) and a no-next async terminal handler.
     *
     * @param mws ordered list of sync middleware
     * @param h   async two-param terminal handler (no {@code next})
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("GET",     path, buildListA(mws, h)); return this; }
    /** POST — middleware list + async no-next terminal. */
    public RouteBuilder postAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("POST",    path, buildListA(mws, h)); return this; }
    /** PUT — middleware list + async no-next terminal. */
    public RouteBuilder putAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PUT",     path, buildListA(mws, h)); return this; }
    /** DELETE — middleware list + async no-next terminal. */
    public RouteBuilder deleteAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("DELETE",  path, buildListA(mws, h)); return this; }
    /** PATCH — middleware list + async no-next terminal. */
    public RouteBuilder patchAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("PATCH",   path, buildListA(mws, h)); return this; }
    /** OPTIONS — middleware list + async no-next terminal. */
    public RouteBuilder optionsAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("OPTIONS", path, buildListA(mws, h)); return this; }
    /** HEAD — middleware list + async no-next terminal. */
    public RouteBuilder headAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("HEAD",    path, buildListA(mws, h)); return this; }
    /** ALL — middleware list + async no-next terminal. */
    public RouteBuilder allAsync(List<RouteHandler> mws, AsyncSimpleHandler h) { registry.addRoute("ALL",     path, buildListA(mws, h)); return this; }

    private List<RouteHandler> buildListA(RouteHandler[] mws, AsyncRouteHandler h) {
        List<RouteHandler> list = new ArrayList<>(mws.length + 1);
        list.addAll(Arrays.asList(mws));
        list.add(Router.async(h));
        return list;
    }

    private List<RouteHandler> buildListA(RouteHandler[] mws, AsyncSimpleHandler h) {
        List<RouteHandler> list = new ArrayList<>(mws.length + 1);
        list.addAll(Arrays.asList(mws));
        list.add(Router.async(h));
        return list;
    }

    private List<RouteHandler> buildListA(List<RouteHandler> mws, AsyncRouteHandler h) {
        List<RouteHandler> list = new ArrayList<>(mws.size() + 1);
        list.addAll(mws);
        list.add(Router.async(h));
        return list;
    }

    private List<RouteHandler> buildListA(List<RouteHandler> mws, AsyncSimpleHandler h) {
        List<RouteHandler> list = new ArrayList<>(mws.size() + 1);
        list.addAll(mws);
        list.add(Router.async(h));
        return list;
    }

    //  Async (use getAsync/postAsync etc.)

    /**
     * Register an async GET handler. Future rejection is forwarded to the error handler.
     *
     * <pre>{@code
     * app.route("/users")
     *    .getAsync((req, res, next) -> db.findAllAsync().thenAccept(u -> res.json(u)));
     * }</pre>
     *
     * @param h async handler returning {@code CompletableFuture<Void>}
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(AsyncRouteHandler h) { return get(Router.async(h)); }
    /** Async POST — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder postAsync(AsyncRouteHandler h) { return post(Router.async(h)); }
    /** Async PUT — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder putAsync(AsyncRouteHandler h) { return put(Router.async(h)); }
    /** Async DELETE — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder deleteAsync(AsyncRouteHandler h) { return delete(Router.async(h)); }
    /** Async PATCH — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder patchAsync(AsyncRouteHandler h) { return patch(Router.async(h)); }
    /** Async OPTIONS — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder optionsAsync(AsyncRouteHandler h) { return options(Router.async(h)); }
    /** Async HEAD — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder headAsync(AsyncRouteHandler h) { return head(Router.async(h)); }
    /** Async ALL — see {@link #getAsync(AsyncRouteHandler)}. */
    public RouteBuilder allAsync(AsyncRouteHandler h) { return all(Router.async(h)); }

    // AsyncSimpleHandler (no-next) — use getAsync/postAsync etc.

    /**
     * Async GET, no-next — use {@code getAsync()} for unambiguous lambda registration.
     *
     * @param h async two-param handler (no {@code next})
     * @return this builder for chaining additional verbs
     */
    public RouteBuilder getAsync(AsyncSimpleHandler h) { return get(Router.async(h)); }
    /** Async POST, no-next. */
    public RouteBuilder postAsync(AsyncSimpleHandler h) { return post(Router.async(h)); }
    /** Async PUT, no-next. */
    public RouteBuilder putAsync(AsyncSimpleHandler h) { return put(Router.async(h)); }
    /** Async DELETE, no-next. */
    public RouteBuilder deleteAsync(AsyncSimpleHandler h) { return delete(Router.async(h)); }
    /** Async PATCH, no-next. */
    public RouteBuilder patchAsync(AsyncSimpleHandler h) { return patch(Router.async(h)); }
    /** Async OPTIONS, no-next. */
    public RouteBuilder optionsAsync(AsyncSimpleHandler h) { return options(Router.async(h)); }
    /** Async HEAD, no-next. */
    public RouteBuilder headAsync(AsyncSimpleHandler h) { return head(Router.async(h)); }
    /** Async ALL, no-next. */
    public RouteBuilder allAsync(AsyncSimpleHandler h) { return all(Router.async(h)); }


    /**
     * Wrap a two-param {@link SimpleHandler} into a three-param {@link RouteHandler}.
     * Automatically calls {@code next.run()} if the response is not committed after the handler returns.
     *
     * @param h the handler to wrap
     * @return a full {@link RouteHandler} that auto-advances the chain
     */
    private static RouteHandler wrap(SimpleHandler h) {
        return (req, res, next) -> {
            h.handle(req, res);
            if (!res.isCommitted()) next.run();
        };
    }
}
