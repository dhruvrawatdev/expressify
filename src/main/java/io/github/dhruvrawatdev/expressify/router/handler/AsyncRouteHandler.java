package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Async three-parameter handler — use when you need to call {@code next()} explicitly
 * after async I/O (e.g. auth middleware that loads a user then proceeds).
 *
 * <p>Pass directly via an explicit cast or a typed variable; no wrapper method is needed:
 *
 * <pre>{@code
 * // Typed variable (clearest)
 * AsyncRouteHandler auth = (req, res, next) ->
 *     tokenService.verifyAsync(req.header("Authorization"))
 *         .thenAccept(user -> {
 *             req.locals().put("user", user);
 *             try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
 *         });
 * app.use(auth);
 *
 * // Explicit cast — works inline
 * app.use((AsyncRouteHandler)(req, res, next) ->
 *     tokenService.verifyAsync(req.header("Authorization"))
 *         .thenAccept(user -> {
 *             req.locals().put("user", user);
 *             try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
 *         })
 * );
 *
 * // Named sugar for routes
 * app.getAsync("/users", (req, res, next) ->
 *     userService.findAllAsync().thenAccept(users -> res.json(users))
 * );
 *
 * // Rejected futures auto-call next(err)
 * app.get("/risky", (AsyncRouteHandler)(req, res, next) ->
 *     CompletableFuture.failedFuture(new RuntimeException("oops"))
 * );
 * }</pre>
 *
 * <p>The framework awaits the returned future on the worker thread. Threading is always
 * controlled by the caller's executor — the framework never spawns threads.
 */
@FunctionalInterface
public interface AsyncRouteHandler {
    CompletableFuture<Void> handle(Request req, Response res, NextFunction next) throws Exception;
}
