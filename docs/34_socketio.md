# 34 — Socket.IO

Socket.IO is a higher-level real-time library built on top of WebSocket. It adds:
- **Events** — named messages instead of raw text
- **Rooms** — broadcast to groups of clients
- **Namespaces** — separate channels on the same server
- **Acknowledgements** — callbacks when the other side confirms receipt
- **Middleware** — run code when clients connect

Expressify's Socket.IO (`ExpressifyIO`) is compatible with the JavaScript Socket.IO v4 client library.

**Note:** Only WebSocket transport is supported. HTTP long-polling is intentionally not implemented.

---

## Quick start

### Server

```java
import io.github.dhruvrawatdev.expressify.socket.ExpressifyIO;

Expressify app = new Expressify();
ExpressifyIO io = new ExpressifyIO();

// Attach BEFORE calling app.listen()
io.attach(app);

io.on("connection", socket -> {
    System.out.println("Connected: " + socket.id());

    socket.on("chat message", args -> {
        String message = (String) args[0];
        io.emit("chat message", message);  // broadcast to all
    });

    socket.on("disconnect", args -> {
        System.out.println("Disconnected: " + socket.id());
    });
});

app.listen(3000);
```

### JavaScript client

```html
<script src="https://cdn.socket.io/4.x/socket.io.min.js"></script>
<script>
    const socket = io('http://localhost:3000');

    socket.on('connect', () => {
        console.log('Connected! ID:', socket.id);
    });

    socket.on('chat message', (msg) => {
        console.log('Message:', msg);
    });

    socket.emit('chat message', 'Hello!');
</script>
```

---

## The `io` object — server-level operations

### `io.on("connection", handler)` — handle new connections

```java
io.on("connection", socket -> {
    // Called for every new client that connects
    System.out.println("New client: " + socket.id());
});
```

### `io.emit(event, ...args)` — broadcast to ALL clients

```java
io.emit("news", "Breaking news!");
io.emit("update", Map.of("type", "weather", "temp", 72));
io.emit("message", "Hello", "World");  // multiple args
```

### `io.to(room).emit(...)` — send to a specific room

```java
io.to("room1").emit("news", "Room 1 message");
io.to("admins").emit("alert", "New signup");
```

### `io.of(namespace)` — access a namespace

```java
Namespace adminNs = io.of("/admin");
adminNs.on("connection", socket -> {
    System.out.println("Admin connected: " + socket.id());
});
```

---

## The `socket` object — per-connection operations

### `socket.id` — unique socket ID

```java
socket.id()  // "abc123xyz" — unique per connection
```

### `socket.on(event, handler)` — listen for events

```java
socket.on("chat", args -> {
    String message = (String) args[0];
    Integer userId  = (Integer) args[1];
    System.out.println(userId + ": " + message);
});
```

The `args` array contains all the arguments the client sent.

### `socket.emit(event, ...args)` — send an event to this client

```java
socket.emit("hello", "World");
socket.emit("user data", Map.of("name", "Alice", "id", 42));
socket.emit("multiple", "arg1", "arg2", 42, true);
```

### `socket.broadcast().emit(...)` — send to all EXCEPT this client

```java
socket.broadcast().emit("user joined", socket.id());
// All other clients receive this; the sender does NOT
```

### Disconnect

```java
socket.on("disconnect", args -> {
    String reason = args.length > 0 ? (String) args[0] : "unknown";
    System.out.println("Disconnected: " + reason);
});
```

---

## Rooms — groups of sockets

Rooms are named channels. A socket can be in multiple rooms.

### Join a room

```java
socket.on("join room", args -> {
    String room = (String) args[0];
    socket.join(room);
    System.out.println(socket.id() + " joined " + room);
});
```

### Leave a room

```java
socket.on("leave room", args -> {
    String room = (String) args[0];
    socket.leave(room);
});
```

### Send to a room (excluding sender)

```java
socket.on("room message", args -> {
    String room    = (String) args[0];
    String message = (String) args[1];

    // Send to everyone in the room EXCEPT the sender
    socket.to(room).emit("room msg", message);
});
```

### Send to a room (including sender)

```java
// From the io object — includes everyone in the room
io.to("room1").emit("room msg", "Hello room!");
```

---

## Acknowledgements — confirm message receipt

### Client requests acknowledgement, server sends it

```java
socket.on("save data", args -> {
    Object data = args[0];
    // Last arg is an AckCallback if client requested ack
    if (args.length > 1 && args[args.length - 1] instanceof AckCallback ack) {
        // Save the data...
        boolean saved = db.save(data);
        ack.call(saved);  // send result back to client
    }
});
```

JavaScript client:
```js
socket.emit('save data', myData, (result) => {
    console.log('Saved:', result);  // true or false
});
```

### Server requests acknowledgement from client

```java
// Send and get a response back (CompletableFuture style)
socket.emitWithAck("getData", response -> {
    System.out.println("Client responded: " + response[0]);
});

// Or as CompletableFuture
socket.emitWithAck("ping")
    .thenAccept(args -> System.out.println("Pong received!"));
```

---

## Namespaces — separate channels

Namespaces let you split your app into separate sections. Each namespace has its own event handlers and middleware.

```java
// Default namespace — "/"
io.on("connection", socket -> { ... });

// Custom namespace — "/admin"
Namespace adminNs = io.of("/admin");
adminNs.on("connection", socket -> {
    System.out.println("Admin connected: " + socket.id());
    socket.emit("hello", "Welcome to admin!");
});

// Custom namespace — "/chat"
Namespace chatNs = io.of("/chat");
chatNs.on("connection", socket -> {
    socket.on("message", args -> chatNs.emit("message", args[0]));
});
```

JavaScript client connecting to a namespace:
```js
const adminSocket = io('http://localhost:3000/admin');
const chatSocket  = io('http://localhost:3000/chat');
```

---

## Middleware — run code before connection

Use middleware to authenticate clients before they can connect:

```java
io.use((socket, next) -> {
    String token = (String) socket.handshake().auth().get("token");
    if (!"valid-token".equals(token)) {
        throw new RuntimeException("Authentication failed");  // rejects connection
    }
    socket.data().put("userId", getUserIdFromToken(token));
    next.run();  // allow connection
});

io.on("connection", socket -> {
    String userId = (String) socket.data().get("userId");
    System.out.println("Authenticated user: " + userId);
});
```

JavaScript client sending auth:
```js
const socket = io('http://localhost:3000', {
    auth: { token: 'valid-token' }
});
```

---

## Handshake info

```java
io.on("connection", socket -> {
    Handshake hs = socket.handshake();

    String query    = hs.query().get("room");   // query params from connection URL
    Object token    = hs.auth().get("token");   // auth data
    String address  = hs.address();             // client IP
    String headers  = hs.headers().get("user-agent");  // request headers
});
```

---

## socket.data() — store per-socket data

```java
io.use((socket, next) -> {
    socket.data().put("username", "alice");
    next.run();
});

io.on("connection", socket -> {
    String name = (String) socket.data().get("username");  // "alice"
});
```

---

## Complete chat application

```java
Expressify app = new Expressify();
ExpressifyIO io = new ExpressifyIO();
io.attach(app);

// Optional: auth middleware
io.use((socket, next) -> {
    String name = (String) socket.handshake().auth().getOrDefault("name", "Anonymous");
    socket.data().put("name", name);
    next.run();
});

io.on("connection", socket -> {
    String name = (String) socket.data().get("name");

    // Announce new user
    socket.broadcast().emit("user joined", name);
    socket.emit("welcome", "Hello " + name + "!");

    // Chat message
    socket.on("chat", args -> {
        String msg = (String) args[0];
        io.emit("chat", Map.of("name", name, "message", msg));
    });

    // Join a room
    socket.on("join room", args -> {
        String room = (String) args[0];
        socket.join(room);
        io.to(room).emit("room joined", name + " joined " + room);
    });

    // Room message
    socket.on("room msg", args -> {
        String room = (String) args[0];
        String msg  = (String) args[1];
        socket.to(room).emit("room msg", Map.of("name", name, "msg", msg));
    });

    // Disconnect
    socket.on("disconnect", args -> {
        socket.broadcast().emit("user left", name);
    });
});

app.use(ServeStatic.create("public"));
app.listen(3000);
```

---

## Quick reference

| Method | Description |
|---|---|
| `new ExpressifyIO()` | Create Socket.IO server |
| `io.attach(app)` | Attach to Expressify app |
| `io.on("connection", fn)` | Handle new connections |
| `io.emit(event, args...)` | Broadcast to all clients |
| `io.to(room).emit(...)` | Send to room (all clients in room) |
| `io.of(namespace)` | Get/create namespace |
| `io.use(middleware)` | Add connection middleware |
| `io.close()` | Close the server |
| `socket.id()` | Unique socket ID |
| `socket.on(event, fn)` | Listen for events |
| `socket.emit(event, args...)` | Send to this client |
| `socket.broadcast().emit(...)` | Send to all EXCEPT this client |
| `socket.join(room)` | Join a room |
| `socket.leave(room)` | Leave a room |
| `socket.to(room).emit(...)` | Send to room (excluding sender) |
| `socket.emitWithAck(event, cb)` | Send with callback |
| `socket.emitWithAck(event)` | Send, returns CompletableFuture |
| `socket.data()` | Per-socket data Map |
| `socket.handshake()` | Connection handshake info |
| `socket.disconnect()` | Disconnect this client |
| `socket.connected()` | Is client connected? |

---

## Next steps

- [33 — WebSocket](33_websocket.md)
- [35 — Exception Types](35_exceptions.md)
