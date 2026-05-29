package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Two-parameter terminating handler — no {@code next} parameter.
 * The framework automatically calls {@code next.run()} after the handler returns
 * if the response has not yet been committed, so it can be safely used as a
 * middleware as well as a terminal route handler.
 *
 * <p>This is the preferred handler type for most route handlers — use
 * {@link RouteHandler} only when you need explicit control over {@code next}.
 *
 * <pre>{@code
 * // Terminal route handler
 * app.get("/hello", (req, res) -> res.send("Hello, World!"));
 *
 * // With body and status
 * app.post("/users", (req, res) -> {
 *     User user = create(req.body());
 *     res.status(201).json(user);
 * });
 *
 * // As middleware — framework calls next() because response is not committed
 * app.use((req, res) -> res.header("X-Custom", "value"));
 *
 * // With path parameter
 * app.get("/users/:id", (req, res) -> {
 *     String id = req.param("id");
 *     res.json(findUser(id));
 * });
 * }</pre>
 *
 * @see RouteHandler      three-param variant with explicit {@code next} control
 * @see AsyncSimpleHandler async two-param variant returning {@code CompletableFuture<Void>}
 */
@FunctionalInterface
public interface SimpleHandler {
    /**
     * Handle an HTTP request without managing chain advancement.
     * The framework calls {@code next()} automatically if the response is not committed on return.
     *
     * @param req the current HTTP request
     * @param res the current HTTP response — call send/json/render to commit it
     * @throws Exception any uncaught exception is forwarded to the error handler automatically
     */
    void handle(Request req, Response res) throws Exception;
}
