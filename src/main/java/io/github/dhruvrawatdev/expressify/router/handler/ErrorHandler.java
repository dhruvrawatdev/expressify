package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Four-parameter error handler — receives the thrown error, request, response, and next.
 * Mirrors Express.js error-handling middleware: {@code (err, req, res, next) => void}.
 *
 * <p>Register with {@code app.error(handler)} or {@code router.error(handler)},
 * always <strong>after</strong> all normal routes and middleware so it catches errors
 * from the full chain.
 *
 * <pre>{@code
 * // Simple catch-all
 * app.error((err, req, res, next) -> {
 *     res.status(500).json(Map.of("error", err.getMessage()));
 * });
 *
 * // Type-aware handler — pass unknown errors to the next error handler
 * app.error((err, req, res, next) -> {
 *     if (err instanceof NotFoundException) {
 *         res.status(404).json(Map.of("error", err.getMessage()));
 *     } else if (err instanceof ValidationException) {
 *         res.status(400).json(Map.of("errors", ((ValidationException) err).getErrors()));
 *     } else {
 *         next.error(err);  // forward to the next error handler in the chain
 *     }
 * });
 * }</pre>
 *
 * @see AsyncErrorHandler async variant returning {@code CompletableFuture<Void>}
 * @see NextFunction      for the {@code next} parameter semantics
 */
@FunctionalInterface
public interface ErrorHandler {
    /**
     * Handle an error that was propagated via {@code next.error(t)} or thrown uncaught.
     *
     * @param err  the error — inspect its type to determine the appropriate response
     * @param req  the current HTTP request
     * @param res  the current HTTP response — send a response to terminate error handling
     * @param next call {@code next.error(err)} to pass to the next error handler,
     *             or {@code next.run()} to resume normal processing (unusual)
     * @throws Exception propagated to the next error handler if thrown
     */
    void handle(Throwable err, Request req, Response res, NextFunction next) throws Exception;
}
