# 33 — WebSocket

WebSocket allows **two-way real-time communication** between the server and client. Unlike HTTP, the connection stays open — both sides can send messages at any time.

Use cases: chat apps, live notifications, real-time dashboards, multiplayer games, collaborative editing.

---

## Quick start

```java
Expressify app = new Expressify();

app.ws("/chat", (ws, req) -> {
    // ws = the WebSocket connection
    // req = the upgrade request (headers, params, query, etc.)

    ws.onMessage(msg -> {
        System.out.println("Received: " + msg.asText());
        ws.send("Echo: " + msg.asText());
    });

    ws.onClose(ev -> System.out.println("Client disconnected"));
});

app.listen(3000);
```

JavaScript client:
```js
const ws = new WebSocket('ws://localhost:3000/chat');
ws.onmessage = (event) => console.log(event.data);
ws.onopen = () => ws.send('Hello!');
```

---

## `app.ws(path, handler)` — register a WebSocket endpoint

```java
app.ws("/path", (ws, req) -> {
    // Called once when a client connects
    // Register your listeners here
});
```

Path params work too:

```java
app.ws("/rooms/:roomId", (ws, req) -> {
    String room = req.param("roomId");
    ws.onMessage(msg -> ws.send("[" + room + "] " + msg.asText()));
});
```

---

## WsSocket — the connection object

### Receiving messages — `ws.onMessage()`

```java
ws.onMessage(msg -> {
    if (msg.isText()) {
        String text = msg.asText();
        // handle text message
    }
    if (msg.isBinary()) {
        byte[] data = msg.asBytes();
        // handle binary message
    }
});
```

### Connection opened — `ws.onOpen()`

```java
ws.onOpen(() -> {
    System.out.println("Client connected!");
    ws.send("Welcome!");  // send immediately on open
});
```

**Note:** You must use `ws.onOpen()` if you want to send on connect. Calling `ws.send()` directly in the connection handler does NOT work — the connection isn't open yet at that point.

### Connection closed — `ws.onClose()`

```java
ws.onClose(ev -> {
    System.out.println("Close code: " + ev.code());
    System.out.println("Reason: " + ev.reason());
});
```

Common close codes:
- `1000` — normal close
- `1001` — going away (browser closed tab)
- `1006` — abnormal (connection lost)

### Errors — `ws.onError()`

```java
ws.onError(err -> {
    System.err.println("WebSocket error: " + err.getMessage());
});
```

### Ping / Pong — `ws.onPing()`, `ws.onPong()`

```java
ws.onPing(data -> {
    System.out.println("Got ping!");
    // autoPong is true by default — pong is sent automatically
});

ws.onPong(data -> {
    ws.setAlive(true);  // mark client as alive (for heartbeat)
});
```

---

## Sending messages

### Send text

```java
ws.send("Hello, client!");
ws.send("Received: " + data);
```

### Send binary

```java
ws.send(new byte[]{1, 2, 3, 4});
ws.send(imageBytes);
```

### Send with callback

```java
ws.send("message", new WebSocketCallback<Void>() {
    @Override
    public void complete(WebSocketChannel channel, Void context) {
        System.out.println("Sent successfully");
    }
    @Override
    public void onError(WebSocketChannel channel, Void context, Throwable err) {
        System.err.println("Send failed: " + err.getMessage());
    }
});
```

---

## Closing the connection

### Server-initiated close

```java
ws.close();               // close with code 1000 (Normal)
ws.close(1001);           // close with code
ws.close(1000, "Bye!");   // close with code + reason
```

### Terminate (force close, no handshake)

```java
ws.terminate();  // immediately kill the connection
```

Use `terminate()` for unresponsive clients (see heartbeat below).

---

## Connection state

```java
ws.readyState();    // CONNECTING, OPEN, CLOSING, CLOSED
ws.isOpen();        // true if readyState == OPEN
ws.isAlive();       // true by default; set false and check with ping/pong
ws.setAlive(true);  // set alive status manually
```

---

## Per-connection data — `ws.locals()`

```java
app.ws("/chat", (ws, req) -> {
    // Store data on this connection
    ws.locals().put("username", req.query("name"));
    ws.locals().put("joinedAt", System.currentTimeMillis());

    ws.onMessage(msg -> {
        String name = (String) ws.locals().get("username");
        ws.send("[" + name + "]: " + msg.asText());
    });
});
```

---

## WsServer — for broadcast and client tracking

Use `app.wsServer()` instead of `app.ws()` when you need to:
- Broadcast to all connected clients
- Track how many clients are connected
- Iterate over all connections

```java
WsServer chat = app.wsServer("/chat");

chat.onConnection((ws, req) -> {
    String name = req.query("name");

    // Announce new user
    chat.broadcast(name + " joined");

    ws.onMessage(msg -> {
        // Broadcast to ALL (including sender)
        chat.broadcast("[" + name + "]: " + msg.asText());
    });

    ws.onClose(ev -> {
        chat.broadcast(name + " left");
    });
});
```

### Broadcast to all clients

```java
chat.broadcast("Server announcement!");
chat.broadcast(binaryData);
```

### Broadcast excluding sender

```java
ws.onMessage(msg -> {
    chat.broadcast(msg.asText(), ws);  // everyone EXCEPT ws
});
```

### Get connected clients

```java
Set<WsSocket> clients = chat.clients();
int count = chat.clientCount();

// Send to specific clients
for (WsSocket client : chat.clients()) {
    if (client.isOpen()) {
        client.send("Hello!");
    }
}
```

---

## WsServer options

```java
WsServer wss = app.wsServer("/secure",
    WsServerOptions.builder()
        .clientTracking(true)            // track clients (default: true)
        .maxPayload(64 * 1024)           // max message size: 64 KB
        .autoPong(true)                  // auto-send pong for ping (default: true)

        // Authentication — reject connections if this returns false
        .verifyClient(info -> {
            String token = info.req().get("Authorization");
            return "Bearer secret".equals(token);
        })

        // Subprotocol negotiation
        .subprotocols("json.v1", "json.v2")

        // Custom protocol selection
        .handleProtocols((offered, req) -> {
            if (offered.contains("json.v1")) return "json.v1";
            return offered.get(0);
        })
        .build()
);
```

---

## Heartbeat — detect dead connections

Detect clients that disconnected without sending a close frame:

```java
WsServer wss = app.wsServer("/ws");

wss.onConnection((ws, req) -> {
    ws.onPong(data -> ws.setAlive(true));  // client responded to ping
});

// Run heartbeat every 30 seconds
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(() -> {
    for (WsSocket ws : wss.clients()) {
        if (!ws.isAlive()) {
            ws.terminate();  // dead connection — kill it
        } else {
            ws.setAlive(false);  // assume dead until pong arrives
            ws.ping();           // send ping — client should pong back
        }
    }
}, 30, 30, TimeUnit.SECONDS);
```

---

## Server-level events

```java
WsServer wss = app.wsServer("/ws");

// Called when server shuts down
wss.onClose(() -> System.out.println("Server closing"));

// Called when a server-level error occurs
wss.onError(err -> System.err.println("Server error: " + err.getMessage()));
```

---

## Pre-configured WsServer

Build a WsServer outside of the app, then register it:

```java
// Create
WsServer myServer = new WsServer("/chat",
    WsServerOptions.builder().clientTracking(true).build()
);
myServer.onConnection((ws, req) -> ws.onMessage(msg -> ws.send(msg.asText())));

// Register
app.ws(myServer);  // or: app.wsServer(path) and configure separately
```

---

## Complete chat example

```java
Expressify app = new Expressify();

// WebSocket endpoint
WsServer chat = app.wsServer("/chat", WsServerOptions.builder()
    .clientTracking(true).build());

chat.onConnection((ws, req) -> {
    String name = req.query("name") != null ? req.query("name") : "Anonymous";

    // Announce
    chat.broadcast(name + " joined. " + chat.clientCount() + " online.");

    ws.onOpen(() -> ws.send("Welcome, " + name + "!"));

    ws.onMessage(msg -> {
        if ("PING".equals(msg.asText())) {
            ws.send("PONG");
        } else {
            chat.broadcast("[" + name + "]: " + msg.asText(), ws);
        }
    });

    ws.onClose(ev -> chat.broadcast(name + " left."));
});

// Also serve static files (chat UI)
app.use(ServeStatic.create("public"));

app.listen(3000, () -> System.out.println("Chat server: http://localhost:3000"));
```

---

## Quick reference

| Method | Description |
|---|---|
| `app.ws(path, handler)` | Register WebSocket endpoint |
| `app.wsServer(path)` | Create WsServer for broadcast |
| `app.wsServer(path, options)` | Create WsServer with options |
| `ws.onMessage(fn)` | Listen for messages |
| `ws.onOpen(fn)` | Listen for connection open |
| `ws.onClose(fn)` | Listen for connection close |
| `ws.onError(fn)` | Listen for errors |
| `ws.onPing(fn)` | Listen for ping |
| `ws.onPong(fn)` | Listen for pong |
| `ws.send(text)` | Send text message |
| `ws.send(bytes)` | Send binary message |
| `ws.ping()` | Send ping |
| `ws.pong()` | Send pong |
| `ws.close()` | Graceful close |
| `ws.terminate()` | Force close |
| `ws.isOpen()` | Is connection open? |
| `ws.isAlive()` | Is client alive? |
| `ws.setAlive(bool)` | Set alive flag |
| `ws.readyState()` | CONNECTING/OPEN/CLOSING/CLOSED |
| `ws.locals()` | Per-connection data Map |
| `wss.broadcast(msg)` | Send to all clients |
| `wss.broadcast(msg, exclude)` | Send to all except one |
| `wss.clients()` | Set of all connected clients |
| `wss.clientCount()` | Number of connected clients |
| `wss.onConnection(fn)` | Listen for new connections |
| `wss.onClose(fn)` | Listen for server close |
| `wss.onError(fn)` | Listen for server errors |

---

## Next steps

- [34 — Socket.IO](34_socketio.md)
