package io.github.dhruvrawatdev.expressify.socket.internal;

import io.undertow.websockets.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.Pooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Engine.IO v4 transport layer wrapping an Undertow {@link WebSocketChannel}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Sends the EIO OPEN handshake ({@code 0{...}}) on connection</li>
 *   <li>Handles heartbeat: emits {@code 2} (ping) every {@code pingInterval} ms,
 *       disconnects if no {@code 3} (pong) received within {@code pingTimeout} ms</li>
 *   <li>Strips the EIO type prefix from incoming frames and passes Socket.IO
 *       payload strings to the registered {@link #onMessage} handler</li>
 *   <li>Prefixes outgoing Socket.IO strings with {@code '4'} (EIO MESSAGE)</li>
 * </ul>
 *
 * <p>Engine.IO packet types: 0=open, 1=close, 2=ping, 3=pong, 4=message, 6=noop.
 */
public final class EngineSocket {

    private static final Logger log = LoggerFactory.getLogger(EngineSocket.class);

    private static final char EIO_OPEN = '0';
    private static final char EIO_CLOSE = '1';
    private static final char EIO_PING = '2';
    private static final char EIO_PONG = '3';
    private static final char EIO_MESSAGE = '4';
    private static final char EIO_NOOP = '6';

    private final WebSocketChannel channel;
    private final String sid;
    private final long  pingInterval;
    private final long  pingTimeout;

    private Consumer<String> onMessage;    // called with SIO payload (no EIO prefix)
    private Runnable onClose;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pingTask;
    private volatile ScheduledFuture<?> pingTimeoutTask;
    private volatile boolean waitingForPong = false;

    public EngineSocket(WebSocketChannel channel, String sid, long pingInterval, long pingTimeout) {
        this.channel = channel;
        this.sid = sid;
        this.pingInterval = pingInterval;
        this.pingTimeout = pingTimeout;

        channel.addCloseTask(ch -> handleClose());
    }

    // Lifecycle

    /** Send the EIO OPEN packet and start the heartbeat scheduler. */
    public void open(ScheduledExecutorService scheduler, long maxPayload) {
        String openData = EIO_OPEN + "{\"sid\":\"" + sid
                + "\",\"upgrades\":[],\"pingInterval\":" + pingInterval
                + ",\"pingTimeout\":" + pingTimeout
                + ",\"maxPayload\":" + maxPayload + "}";
        sendRaw(openData);
        startHeartbeat(scheduler);
        channel.getReceiveSetter().set(buildReceiveListener());
        channel.resumeReceives();
    }

    /** Close the underlying WebSocket channel. */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cancelHeartbeat();
            try {
                sendRaw(String.valueOf(EIO_CLOSE));
                channel.close();
            } catch (IOException ignored) {}
        }
    }

    public boolean isClosed() { return closed.get(); }

    public String sid() { return sid; }

    public String remoteAddress() {
        return channel.getSourceAddress() != null
               ? channel.getSourceAddress().toString() : "unknown";
    }

    //  Event handlers (called by higher layers)

    public void onMessage(Consumer<String> handler)  { this.onMessage = handler; }
    public void onClose(Runnable handler)            { this.onClose   = handler; }

    // Sending

    /** Send a Socket.IO packet string (will be wrapped with EIO message prefix). */
    public void sendMessage(String sioPayload) {
        if (!closed.get()) sendRaw(EIO_MESSAGE + sioPayload);
    }

    private void sendRaw(String text) {
        if (!channel.isOpen()) return;
        WebSockets.sendText(text, channel, new WebSocketCallback<Void>() {
            @Override public void complete(WebSocketChannel c, Void ctx) {}
            @Override public void onError(WebSocketChannel c, Void ctx, Throwable t) {
                log.debug("EIO send error for sid {}", sid, t);
            }
        });
    }

    // Heartbeat

    private void startHeartbeat(ScheduledExecutorService scheduler) {
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            if (waitingForPong) {
                log.debug("EIO heartbeat timeout for sid {}", sid);
                close();
                return;
            }
            waitingForPong = true;
            sendRaw(String.valueOf(EIO_PING));
            pingTimeoutTask = scheduler.schedule(() -> {
                if (waitingForPong && !closed.get()) {
                    log.debug("EIO pong timeout for sid {}", sid);
                    close();
                }
            }, pingTimeout, TimeUnit.MILLISECONDS);
        }, pingInterval, pingInterval, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (pingTask != null) { pingTask.cancel(false); pingTask = null; }
        if (pingTimeoutTask != null) { pingTimeoutTask.cancel(false); pingTimeoutTask = null; }
    }

    // Receive listener

    private AbstractReceiveListener buildReceiveListener() {
        return new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage msg) {
                String text = msg.getData();
                if (text == null || text.isEmpty()) return;
                char type = text.charAt(0);

                switch (type) {
                    case EIO_PONG -> {
                        waitingForPong = false;
                        if (pingTimeoutTask != null) {
                            pingTimeoutTask.cancel(false);
                            pingTimeoutTask = null;
                        }
                    }
                    case EIO_MESSAGE -> {
                        if (onMessage != null && text.length() > 1) {
                            try { onMessage.accept(text.substring(1)); }
                            catch (Exception e) { log.error("Error in SIO message handler", e); }
                        }
                    }
                    case EIO_CLOSE -> close();
                    case EIO_PING  -> sendRaw(String.valueOf(EIO_PONG)); // client-initiated ping
                    default        -> log.debug("Unknown EIO packet type: {}", type);
                }
            }

            @Override
            protected void onFullBinaryMessage(WebSocketChannel ch, BufferedBinaryMessage msg) {
                // Binary frames are not used in this EIO v4 WebSocket implementation
                Pooled<ByteBuffer[]> pooled = msg.getData();
                try { pooled.free(); } catch (Exception ignored) {}
            }

            @Override
            protected void onError(WebSocketChannel ch, Throwable e) {
                log.debug("EIO WebSocket error for sid {}", sid, e);
                close();
            }

            @Override
            protected void onClose(WebSocketChannel ch, StreamSourceFrameChannel sc) throws IOException {
                handleClose();
                super.onClose(ch, sc);
            }
        };
    }

    private void handleClose() {
        if (closed.compareAndSet(false, true)) {
            cancelHeartbeat();
            if (onClose != null) {
                try { onClose.run(); }
                catch (Exception e) { log.debug("Error in EIO close handler", e); }
            }
        }
    }
}
