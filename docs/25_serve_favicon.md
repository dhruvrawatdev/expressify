# 25 — Serve Favicon

The favicon middleware serves the small icon that appears in browser tabs. It serves it efficiently from memory with proper caching.

---

## From a file

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_favicon.ServeFavicon;

app.use(ServeFavicon.create("public/favicon.ico"));
```

Put this **before** your logger middleware to avoid logging favicon requests, which are noisy.

---

## From bytes

```java
byte[] iconBytes = Files.readAllBytes(Path.of("icon.ico"));
app.use(ServeFavicon.create(iconBytes));
```

---

## With cache options

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_favicon.FaviconOptions;

app.use(ServeFavicon.create("public/favicon.ico",
    FaviconOptions.builder()
        .maxAge(86400)  // cache in browser for 1 day (seconds)
        .build()
));
```

---

## Recommended placement

```java
// 1. Favicon FIRST — before logger so favicon isn't logged
app.use(ServeFavicon.create("public/favicon.ico"));

// 2. Then other middleware
app.use(Expressify.morgan("dev"));
app.use(Expressify.json());

// 3. Routes
app.get("/", (req, res) -> res.send("Home"));
```

---

## How it works

- Only responds to `GET /favicon.ico` requests
- Serves from memory (fast, no disk read per request)
- Sends `ETag` and `Cache-Control` headers so browsers cache it
- All other requests fall through to the next middleware

---

## Next steps

- [23 — Serve Static](23_serve_static.md)
- [04 — Middleware](04_middleware.md)
