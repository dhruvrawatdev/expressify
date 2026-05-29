package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Async two-parameter handler — no {@code next}, returns a {@link CompletableFuture}.
 * The framework awaits completion, then auto-calls {@code next()} if the response was
 * not committed, or {@code next(err)} if the future is rejected.
 * Threading is always controlled by the caller's executor.
 *
 * <pre>{@code
 * app.getAsync("/users", (req, res) ->
 *     db.findAllAsync().thenAccept(res::json)
 * );
 *
 * // Same via overloaded method with explicit cast
 * app.get("/users", (AsyncSimpleHandler) (req, res) ->
 *     db.findAllAsync().thenAccept(res::json)
 * );
 * }</pre>
 */
@FunctionalInterface
public interface AsyncSimpleHandler {
    CompletableFuture<Void> handle(Request req, Response res) throws Exception;
}
