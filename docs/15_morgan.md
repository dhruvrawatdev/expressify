# 15 — Morgan (Request Logger)

Morgan logs every HTTP request to the console. It tells you what method and path was called, the status code, response time, and body size.

---

## Basic usage

```java
app.use(Expressify.morgan("dev"));
```

Output:
```
GET /users 200 12.345 ms - 156
POST /users 201 5.123 ms - 47
GET /users/999 404 2.456 ms - 23
```

---

## Log formats

### `"dev"` — colourful, short format (best for development)

```
GET /users 200 5.123 ms - 156
```

Colours: green for 2xx, yellow for 3xx, red for 4xx/5xx.

### `"combined"` — Apache combined log format (standard for production)

```
127.0.0.1 - - [28/May/2024:12:00:00 +0000] "GET /users HTTP/1.1" 200 156 "-" "Mozilla/5.0"
```

### `"common"` — Apache common log format

```
127.0.0.1 - - [28/May/2024:12:00:00 +0000] "GET /users HTTP/1.1" 200 156
```

### `"short"` — shorter than combined

```
127.0.0.1 - GET /users HTTP/1.1 200 156 - 5.123 ms
```

### `"tiny"` — minimal

```
GET /users 200 156 - 5.123 ms
```

---

## Usage

```java
// Development
app.use(Expressify.morgan("dev"));

// Production
app.use(Expressify.morgan("combined"));

// Minimal
app.use(Expressify.morgan("tiny"));
```

---

## Custom options

```java
import io.github.dhruvrawatdev.expressify.middleware.morgan.Morgan;
import io.github.dhruvrawatdev.expressify.middleware.morgan.MorganOptions;

app.use(Morgan.create("dev",
    MorganOptions.builder()
        .stream(System.err)                    // log to stderr instead of stdout
        .skip((req, res) -> res.getStatus() < 400)  // only log errors
        .immediate(false)                      // log after response (default)
        .build()
));
```

### Skip function — only log certain requests

```java
// Skip health check endpoint
app.use(Morgan.create("dev",
    MorganOptions.builder()
        .skip((req, res) -> req.path().equals("/health"))
        .build()
));

// Only log errors (4xx and 5xx)
app.use(Morgan.create("combined",
    MorganOptions.builder()
        .skip((req, res) -> res.getStatus() < 400)
        .build()
));
```

### Log to a file

```java
import java.io.*;

PrintStream logFile = new PrintStream(new FileOutputStream("access.log", true));

app.use(Morgan.create("combined",
    MorganOptions.builder()
        .stream(logFile)
        .build()
));
```

### Custom stream (for logging frameworks like SLF4J, Logback)

```java
app.use(Morgan.create("combined",
    MorganOptions.builder()
        .stream(msg -> logger.info(msg.trim()))  // pass to your logger
        .build()
));
```

The `stream()` option accepts either a `PrintStream` or any object that accepts a `String`.

---

## Log immediately vs. after response

```java
// Log BEFORE handler runs (immediate: true) — won't have status code
app.use(Morgan.create("combined",
    MorganOptions.builder()
        .immediate(true)
        .build()
));

// Log AFTER response sent (default, immediate: false) — has status code + time
app.use(Morgan.create("dev",
    MorganOptions.builder()
        .immediate(false)
        .build()
));
```

---

## Typical setup

```java
Expressify app = new Expressify();

if (isDevelopment()) {
    app.use(Expressify.morgan("dev"));         // colourful in dev
} else {
    app.use(Expressify.morgan("combined"));    // Apache format in prod
}
```

---

## Quick reference

| Format | Best for |
|---|---|
| `"dev"` | Development — colourful, concise |
| `"combined"` | Production — full Apache log |
| `"common"` | Production — simpler Apache log |
| `"short"` | Moderate detail |
| `"tiny"` | Minimal logging |

| Option | Type | Description |
|---|---|---|
| `stream(PrintStream)` | PrintStream | Where to write logs |
| `skip((req, res) -> bool)` | BiPredicate | When to skip logging |
| `immediate(boolean)` | boolean | Log before (true) or after (false) response |

---

## Next steps

- [16 — Compression](16_compression.md)
- [04 — Middleware](04_middleware.md)
