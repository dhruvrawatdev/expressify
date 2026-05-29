package io.github.dhruvrawatdev.expressify.socket;

/**
 * Socket.IO middleware — intercepts every new connection before the {@code "connection"} event fires.
 *
 * <p>Mirrors the Socket.IO {@code namespace.use((socket, next) => { })} pattern.
 * Call {@code next.run()} to allow the connection to proceed.
 * Throw any {@link Exception} to reject the connection (the client receives a CONNECT_ERROR).
 *
 * <pre>{@code
 * // Token authentication middleware
 * io.use((socket, next) -> {
 *     String token = (String) socket.handshake().auth().get("token");
 *     if (!validate(token)) throw new RuntimeException("Unauthorized");
 *     // Attach user data for later use
 *     socket.data().put("userId", resolveUser(token));
 *     next.run();
 * });
 *
 * // Multiple middleware run in registration order
 * io.use(rateLimitMiddleware);
 * io.use(authMiddleware);
 * }</pre>
 */
@FunctionalInterface
public interface IoMiddleware {
    /**
     * Handle an incoming connection attempt.
     *
     * @param socket the socket that is trying to connect (not yet in the namespace)
     * @param next   call {@code next.run()} to pass to the next middleware or fire the connection event;
     *               do not call it to silently stall (prefer throwing instead)
     * @throws Exception to reject the connection — the message is sent to the client as a CONNECT_ERROR
     */
    void handle(IoSocket socket, Runnable next) throws Exception;
}
