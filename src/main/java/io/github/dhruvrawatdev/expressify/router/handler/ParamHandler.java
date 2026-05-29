package io.github.dhruvrawatdev.expressify.router.handler;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/**
 * Callback invoked when a named route parameter is bound, before any route handler runs.
 * Mirrors Express.js {@code app.param(name, callback)}.
 *
 * <p>Use param handlers to load resources by ID once and share them across all routes
 * that contain the same parameter, eliminating repetitive database queries.
 *
 * <pre>{@code
 * // Load a user whenever ":userId" appears in a matched route
 * app.param("userId", (req, res, next, userId) -> {
 *     User user = db.findUser(userId);
 *     if (user == null) {
 *         res.status(404).json(Map.of("error", "User not found"));
 *         return;  // do NOT call next() — response is committed
 *     }
 *     req.locals().put("user", user);
 *     next.run();  // proceed to the route handler
 * });
 *
 * // Now every route with :userId has the user pre-loaded
 * app.get("/users/:userId", (req, res) -> res.json(req.locals().get("user")));
 * app.put("/users/:userId", (req, res) -> {
 *     User user = (User) req.locals().get("user");
 *     // ... update user
 * });
 * }</pre>
 *
 * @see io.github.dhruvrawatdev.expressify.router.handler.AsyncParamHandler AsyncParamHandler
 *      for the async variant
 */
@FunctionalInterface
public interface ParamHandler {
    /**
     * Called before any route handler when the named parameter is present in the matched path.
     *
     * @param req   the current request
     * @param res   the current response
     * @param next  call {@code next.run()} to proceed to the route handler,
     *              or {@code next.error(t)} to skip to the error handler
     * @param value the string value of the route parameter (e.g. {@code "42"} for {@code /users/42})
     * @throws Exception any exception is automatically forwarded to the error handler
     */
    void handle(Request req, Response res, NextFunction next, String value) throws Exception;
}
