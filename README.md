# Expressify

**Express.js-inspired web framework for Java 17+**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dhruvrawatdev/expressify)](https://central.sonatype.com/artifact/io.github.dhruvrawatdev/expressify)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net)

Expressify brings the simplicity and familiarity of Express.js to the Java ecosystem. Built on embedded [Undertow](https://undertow.io), it requires no servlet container, no XML configuration, and no annotations — just clean, readable code.

---

## Quick Start

**Maven:**
```xml
<dependency>
    <groupId>io.github.dhruvrawatdev</groupId>
    <artifactId>expressify</artifactId>
    <version>2.2.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.github.dhruvrawatdev:expressify:2.2.0'
```

**Your first server:**
```java
import io.github.dhruvrawatdev.expressify.Expressify;

public class Main {
    public static void main(String[] args) {
        Expressify app = new Expressify();

        app.use(Expressify.json());

        app.get("/", (req, res) -> {
            res.send("Hello, World!");
        });

        app.get("/users/:id", (req, res) -> {
            String id = req.param("id");
            res.json(Map.of("userId", id));
        });

        app.listen(3000, () -> System.out.println("Server running on http://localhost:3000"));
    }
}
```

---

## Why Expressify?

| | Expressify | Spring Boot | Spark Java |
|---|---|---|---|
| **Startup time** | ~50 ms | ~2–5 s | ~200 ms |
| **Express.js API** | Full | No | Partial |
| **Embedded server** | Undertow | Tomcat/Netty | Jetty |
| **Config needed** | None | annotations + YAML | None |
| **Built-in middleware** | 20+ | via starters | ~5 |
| **WebSocket + Socket.IO** | Built-in | via STOMP | No |

---

## Express.js vs Expressify

If you know Express.js, you already know Expressify:

```js
// Node.js Express.js
const app = express();
app.use(express.json());
app.get('/hello', (req, res) => res.send('Hello'));
app.listen(3000);
```

```java
// Java Expressify — almost identical
Expressify app = new Expressify();
app.use(Expressify.json());
app.get("/hello", (req, res) -> res.send("Hello"));
app.listen(3000);
```

---

## Routing

```java
app.get("/posts",           (req, res) -> { /* list */ });
app.post("/posts",          (req, res) -> { /* create */ });
app.put("/posts/:id",       (req, res) -> { /* update */ });
app.delete("/posts/:id",    (req, res) -> { /* delete */ });

// Path parameters
String id = req.param("id");

// Query string  ?search=java&page=2
String q    = req.query("search");
String page = req.query("page");

// Wildcards
app.get("/files/*", (req, res) -> { /* serve anything under /files/ */ });
```

**Sub-routers:**
```java
Router userRouter = new Router();
userRouter.get("/",    (req, res) -> { /* GET /users */ });
userRouter.post("/",   (req, res) -> { /* POST /users */ });
userRouter.get("/:id", (req, res) -> { /* GET /users/:id */ });

app.use("/users", userRouter);
```

---

## Middleware

```java
// Built-in middleware
app.use(Expressify.json());                    // parse JSON bodies
app.use(Expressify.urlencoded());              // parse form bodies
app.use(Expressify.cors());                    // CORS headers
app.use(Expressify.morgan("dev"));             // request logging
app.use(Expressify.helmet());                  // security headers
app.use(Expressify.compress());                // gzip compression
app.use(Expressify.staticFiles("public"));     // static file serving
app.use(Expressify.cookieParser());            // cookie parsing

// Custom middleware
app.use((req, res, next) -> {
    System.out.println(req.method() + " " + req.path());
    next.run();
});

// Route-level middleware
app.get("/admin", requireAuth, (req, res) -> {
    res.send("Admin panel");
});
```

---

## Request & Response

```java
// Request
req.param("id")                  // path parameter
req.query("page")                // query string value
req.body()                       // parsed JSON body (Map)
req.bodyAs(User.class)           // body deserialized to POJO
req.header("Authorization")      // request header
req.cookie("sessionId")          // cookie value
req.session("userId")            // session value
req.ip()                         // client IP
req.path()                       // request path
req.method()                     // HTTP method
req.file("avatar")               // uploaded file (Multer)

// Response
res.send("text");                // send plain text
res.json(Map.of("ok", true));    // send JSON
res.status(201).json(data);      // status + JSON
res.redirect("/login");          // redirect
res.header("X-Custom", "value"); // set header
res.cookie("token", value);      // set cookie
res.sendFile("report.pdf");      // send file download
res.render("index", model);      // render template
res.status(204).end();           // empty response
```

---

## Error Handling

```java
// Throw anywhere in a handler
app.get("/posts/:id", (req, res) -> {
    Post post = db.findById(req.param("id"));
    if (post == null) throw new NotFoundException("Post not found");
    res.json(post);
});

// Global error handler (register last)
app.error((err, req, res, next) -> {
    if (err instanceof HttpException http) {
        res.status(http.getStatusCode()).json(Map.of(
            "error",  http.getMessage(),
            "status", http.getStatusCode()
        ));
    } else {
        res.status(500).json(Map.of("error", "Internal Server Error"));
    }
});
```

**Built-in exceptions:** `BadRequestException` (400), `UnauthorizedException` (401), `ForbiddenException` (403), `NotFoundException` (404), `InternalServerErrorException` (500).

---

## Sessions

```java
app.use(SessionMiddleware.configure(
    SessionMiddleware.SessionOptions.builder()
        .secret("my-secret-key")
        .maxAge(3_600_000)   // 1 hour
        .build()
));

// In a handler
req.setSession("userId", "abc123");
String userId = req.session("userId");
req.clearSession();
```

---

## File Uploads (Multer)

```java
var upload = Multer.create(MultipartOptions.builder()
    .maxSize(5 * 1024 * 1024)      // 5 MB
    .allowedFields("avatar")
    .build());

app.post("/profile", upload.single("avatar"), (req, res) -> {
    UploadedFile file = req.file("avatar");
    System.out.println(file.originalFilename() + " — " + file.size() + " bytes");
    res.json(Map.of("uploaded", file.originalFilename()));
});
```

---

## WebSocket

```java
WsServer ws = app.wsServer("/ws/chat");

ws.onConnection((socket, req) -> {
    socket.onOpen(() -> System.out.println("Client connected"));

    socket.onMessage(msg -> {
        ws.broadcast(msg);   // echo to all clients
    });

    socket.onClose(() -> System.out.println("Client disconnected"));
});
```

---

## Socket.IO

```java
ExpressifyIO io = app.socketIO("/socket.io");

io.onConnection(socket -> {
    System.out.println("Connected: " + socket.id());

    socket.on("join-room", data -> {
        String room = (String) data;
        socket.join(room);
        io.to(room).emit("user-joined", socket.id());
    });

    socket.on("message", data -> {
        socket.broadcast().emit("message", data);
    });

    socket.onDisconnect(() -> System.out.println("Disconnected: " + socket.id()));
});
```

---

## Template Engines

```java
// Thymeleaf
app.engine(TemplateEngine.thymeleaf("src/main/resources/templates"));
app.get("/", (req, res) -> res.render("index", Map.of("name", "World")));

// FreeMarker
app.engine(TemplateEngine.freemarker("src/main/resources/templates"));

// Pebble
app.engine(TemplateEngine.pebble("src/main/resources/templates"));

// Also supported: JTE, Velocity, Handlebars
```

---

## Rate Limiting

```java
var limiter = RateLimiter.create(RateLimiterOptions.builder()
    .windowMs(60_000)   // 1 minute
    .max(100)           // 100 requests per window
    .build());

app.use("/api", limiter);
```

---

## Async Handlers

```java
app.getAsync("/data", async (req, res) -> {
    String result = await(fetchFromDatabaseAsync());
    res.json(result);
});

// Or with CompletableFuture directly
app.get("/data", (req, res) -> {
    CompletableFuture.supplyAsync(() -> heavyComputation())
        .thenAccept(result -> res.json(result));
});
```

---

## Built-in Middleware

| Middleware | Description |
|---|---|
| `Expressify.json()` | Parse JSON request bodies |
| `Expressify.urlencoded()` | Parse URL-encoded form bodies |
| `Expressify.text()` | Parse plain text bodies |
| `Expressify.cors()` | CORS headers with full configuration |
| `Expressify.helmet()` | Security HTTP headers (CSP, HSTS, etc.) |
| `Expressify.morgan()` | HTTP request logging (dev, combined, etc.) |
| `Expressify.compress()` | Gzip/Deflate response compression |
| `Expressify.staticFiles()` | Serve static files from a directory |
| `Expressify.cookieParser()` | Parse Cookie header |
| `SessionMiddleware` | Cookie-backed sessions with configurable store |
| `CookieSession` | Signed cookie sessions (no server-side store) |
| `RateLimiter` | Token-bucket rate limiting per client IP |
| `SlowDown` | Progressive request throttling |
| `Multer` | Multipart/form-data file upload handling |
| `ServeIndex` | Directory listing |
| `ServeFavicon` | Serve a favicon.ico |
| `ResponseTime` | Adds `X-Response-Time` header |
| `Timeout` | Abort requests that exceed a time limit |
| `MethodOverride` | Override HTTP method via `_method` field/header |
| `Vhost` | Virtual host routing by hostname |
| `Proxy` | Reverse proxy middleware |
| `Validator` | Declarative request validation |
| `DevErrorHandler` | Detailed HTML error pages in development |
| `RateLimitHeaderParser` | Parse RateLimit-* response headers |

---

## Requirements

- **Java 17+** — Download from [https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html](https://adoptium.net)
- **Maven 3.8+** — [maven.apache.org](https://maven.apache.org)

---

## Documentation

Full documentation is in the [`docs/`](docs/) folder:

| # | Topic |
|---|---|
| [01](docs/01_introduction.md) | Introduction |
| [02](docs/02_installation.md) | Installation & Quick Start |
| [03](docs/03_routing.md) | Routing |
| [04](docs/04_middleware.md) | Middleware |
| [05](docs/05_request.md) | Request Object |
| [06](docs/06_response.md) | Response Object |
| [07](docs/07_error_handling.md) | Error Handling |
| [08](docs/08_settings.md) | App Settings |
| [09](docs/09_template_engines.md) | Template Engines |
| [10](docs/10_async_handlers.md) | Async Handlers |
| [11](docs/11_param_handlers.md) | Parameter Handlers |
| [12](docs/12_body_parser.md) | Body Parser |
| [13](docs/13_cors.md) | CORS |
| [14](docs/14_helmet.md) | Helmet |
| [15](docs/15_morgan.md) | Morgan |
| [16](docs/16_compression.md) | Compression |
| [17](docs/17_session.md) | Sessions |
| [18](docs/18_cookie_parser.md) | Cookie Parser |
| [19](docs/19_cookie_session.md) | Cookie Session |
| [20](docs/20_rate_limiter.md) | Rate Limiter |
| [21](docs/21_slow_down.md) | Slow Down |
| [22](docs/22_multer.md) | Multer (File Uploads) |
| [23](docs/23_serve_static.md) | Serve Static |
| [24](docs/24_serve_index.md) | Serve Index |
| [25](docs/25_serve_favicon.md) | Serve Favicon |
| [26](docs/26_response_time.md) | Response Time |
| [27](docs/27_timeout.md) | Timeout |
| [28](docs/28_method_override.md) | Method Override |
| [29](docs/29_vhost.md) | Virtual Host |
| [30](docs/30_proxy.md) | Proxy |
| [31](docs/31_validator.md) | Validator |
| [32](docs/32_dev_error_handler.md) | Dev Error Handler |
| [33](docs/33_websocket.md) | WebSocket |
| [34](docs/34_socketio.md) | Socket.IO |
| [35](docs/35_exceptions.md) | Exceptions |
| [36](docs/36_ratelimit_header_parser.md) | Rate Limit Header Parser |
| [37](docs/37_complete_example.md) | Complete Example |

---

## Building from Source

```bash
git clone https://github.com/dhruvrawatdev/expressify.git
cd expressify
mvn package
```

This produces:
- `target/expressify-2.2.0.jar` — library JAR
- `target/expressify-2.2.0-all.jar` — fat JAR with all dependencies

---

## License

[MIT](https://opensource.org/licenses/MIT) — Copyright (c) 2024 Dhruv Rawat
