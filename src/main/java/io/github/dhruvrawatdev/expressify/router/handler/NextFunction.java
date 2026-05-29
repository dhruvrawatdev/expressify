package io.github.dhruvrawatdev.expressify.router.handler;

/**
 * Controls advancement through the middleware chain.
 * Passed as the third parameter to every {@link RouteHandler} and {@link ErrorHandler}.
 * Mirrors the Express.js {@code next} callback.
 *
 * <ul>
 *   <li>{@link #run()} — pass control to the next middleware or route handler in the chain.</li>
 *   <li>{@link #error(Throwable)} — skip remaining middleware and jump to the nearest
 *       {@link ErrorHandler}.</li>
 * </ul>
 *
 * <pre>{@code
 * // Middleware that always continues the chain
 * app.use((req, res, next) -> {
 *     req.locals().put("startMs", System.currentTimeMillis());
 *     next.run();
 * });
 *
 * // Middleware that conditionally errors
 * app.use((req, res, next) -> {
 *     String token = req.get("Authorization");
 *     if (token == null || !isValid(token)) {
 *         next.error(new UnauthorizedException("Missing or invalid token"));
 *         return;
 *     }
 *     next.run();
 * });
 *
 * // Error handler that passes the error along
 * app.error((err, req, res, next) -> {
 *     if (err instanceof ValidationException) {
 *         res.status(400).json(Map.of("error", err.getMessage()));
 *     } else {
 *         next.error(err);  // re-throw to the next error handler
 *     }
 * });
 * }</pre>
 *
 * @see RouteHandler  for the handler interface that receives {@code NextFunction}
 * @see ErrorHandler  for the error handler interface that also receives {@code NextFunction}
 */
public interface NextFunction {
    /**
     * Advance to the next middleware or route handler in the chain.
     *
     * @throws Exception propagated if the next handler throws synchronously
     */
    void run() throws Exception;

    /**
     * Skip remaining middleware and route to the nearest registered {@link ErrorHandler}.
     * Equivalent to Express.js {@code next(err)}.
     *
     * @param err the error to forward — accessible as the first parameter of the error handler
     * @throws Exception propagated if the error handler throws synchronously
     */
    void error(Throwable err) throws Exception;
}
