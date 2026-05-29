package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Core handler and middleware interface — {@code (req, res, next) -> void}.
 * Mirrors the Express.js three-parameter callback signature exactly.
 *
 * <p>Every piece of code registered with {@code app.use()}, {@code app.get()}, etc. is
 * ultimately a {@code RouteHandler}. The {@code next} parameter controls chain advancement:
 * <ul>
 *   <li>Call {@code next.run()} to pass control to the next handler in the chain.</li>
 *   <li>Call {@code next.error(t)} to skip directly to the nearest error handler.</li>
 *   <li>Send a response <em>without</em> calling {@code next} to terminate the chain.</li>
 * </ul>
 *
 * <h2>Middleware</h2>
 * <pre>{@code
 * // Logging middleware — must call next.run() to continue the chain
 * app.use((req, res, next) -> {
 *     System.out.println(req.method() + " " + req.path());
 *     next.run();
 * });
 * }</pre>
 *
 * <h2>Route handler</h2>
 * <pre>{@code
 * app.get("/users/:id", (req, res, next) -> {
 *     User user = findUser(req.param("id"));
 *     if (user == null) {
 *         next.error(new NotFoundException("User not found"));
 *         return;
 *     }
 *     res.json(user);  // terminates the chain
 * });
 * }</pre>
 *
 * <p>For terminal route handlers that don't need {@code next}, prefer
 * {@link SimpleHandler} — the framework wraps it automatically and calls
 * {@code next()} on return if the response has not been committed:
 * <pre>{@code
 * app.get("/users", (req, res) -> res.json(users));
 * }</pre>
 *
 * @see SimpleHandler     two-param variant (no {@code next} needed)
 * @see AsyncRouteHandler async three-param variant returning {@code CompletableFuture<Void>}
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * Handle an HTTP request.
     *
     * @param req  the current HTTP request
     * @param res  the current HTTP response — call send/json/render to commit it
     * @param next call {@code next.run()} to advance the chain to the next handler,
     *             or {@code next.error(t)} to route to the error handler
     * @throws Exception any uncaught exception is forwarded to the error handler automatically
     */
    void handle(Request req, Response res, NextFunction next) throws Exception;
}
