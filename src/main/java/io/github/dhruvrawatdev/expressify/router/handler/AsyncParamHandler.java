package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Async variant of {@link ParamHandler} — use when param pre-processing involves
 * async I/O (e.g. a database lookup keyed on the route parameter value).
 *
 * <pre>{@code
 * app.param("id", (AsyncParamHandler)(req, res, next, id) ->
 *     db.findUserAsync(id)
 *         .thenAccept(user -> {
 *             req.locals().put("user", user);
 *             try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
 *         })
 * );
 *
 * // Or via the named overload:
 * router.param("id", (AsyncParamHandler)(req, res, next, id) ->
 *     db.findUserAsync(id).thenAccept(user -> req.locals().put("user", user))
 * );
 * }</pre>
 *
 * <p>The framework awaits the returned future on the worker thread. If the future
 * completes without calling {@code next}, {@code next()} is invoked automatically.
 * If the future is rejected, {@code next(err)} is called automatically.
 * Thread pools are always controlled by the caller's executor — the framework never
 * spawns threads.
 */
@FunctionalInterface
public interface AsyncParamHandler {
    CompletableFuture<Void> handle(Request req, Response res, NextFunction next, String value)
            throws Exception;
}
