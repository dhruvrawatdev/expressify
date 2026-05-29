package io.github.dhruvrawatdev.expressify.socket;

/**
 * Callback invoked when the remote peer acknowledges an emitted event.
 *
 * <p>Mirrors the acknowledgement callback in Node.js Socket.IO:
 * <pre>{@code
 * // JavaScript client
 * socket.emit("order:update", payload, (...args) => {
 *     console.log("ack received", args);
 * });
 * }</pre>
 *
 * <p>Java server — use {@link io.github.dhruvrawatdev.expressify.socket.IoSocket#emitWithAck(String, AckCallback, Object...)}:
 * <pre>{@code
 * // Send event and handle the ack when the client responds
 * socket.emitWithAck("order:update", args -> System.out.println("ack: " + Arrays.toString(args)), payload);
 *
 * // Or use the CompletableFuture variant:
 * socket.emitWithAck("ping")
 *       .thenAccept(args -> System.out.println("pong received"));
 * }</pre>
 */
@FunctionalInterface
public interface AckCallback {
    /**
     * Called when the acknowledgement is received from the client.
     *
     * @param args the acknowledgement arguments sent by the remote peer (may be empty)
     */
    void call(Object... args);
}
