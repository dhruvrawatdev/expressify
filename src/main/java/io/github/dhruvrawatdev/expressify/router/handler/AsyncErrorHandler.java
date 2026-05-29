package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Async error handler — use when error recovery involves async I/O (e.g. logging to a
 * remote system before responding).
 *
 * <p>Register via an explicit cast, a typed variable, or the {@code errorAsync()} sugar:
 *
 * <pre>{@code
 * // Explicit cast
 * app.error((AsyncErrorHandler)(err, req, res, next) ->
 *     errorLogger.logAsync(err)
 *         .thenRun(() -> res.status(500).json(Map.of("error", err.getMessage())))
 * );
 *
 * // Named sugar
 * app.errorAsync((err, req, res, next) ->
 *     errorLogger.logAsync(err)
 *         .thenRun(() -> res.status(500).json(Map.of("error", err.getMessage())))
 * );
 * }</pre>
 *
 * <p>The framework awaits the returned future on the worker thread. Threading is always
 * controlled by the caller's executor — the framework never spawns threads.
 */
@FunctionalInterface
public interface AsyncErrorHandler {
    CompletableFuture<Void> handle(Throwable err, Request req, Response res, NextFunction next)
            throws Exception;
}
