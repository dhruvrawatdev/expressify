# 16 — Compression

The compression middleware compresses response bodies using **gzip** or **deflate**. Compressed responses are smaller, so they transfer faster — especially for JSON APIs and HTML pages.

The browser automatically decompresses them, so your users don't see any difference.

---

## Basic usage

```java
app.use(Expressify.compression());
```

That's it! Responses will now be gzip-compressed if:
- The client sends `Accept-Encoding: gzip` (all modern browsers do)
- The response body is large enough to benefit from compression

---

## How it works

1. Client sends: `Accept-Encoding: gzip, deflate`
2. Your route sends: `res.json(largeData)` (uncompressed)
3. Compression middleware intercepts and compresses the response
4. Server sends: compressed bytes + `Content-Encoding: gzip` header
5. Browser decompresses automatically — user sees the original data

---

## Custom options

```java
import io.github.dhruvrawatdev.expressify.middleware.compression.Compression;
import io.github.dhruvrawatdev.expressify.middleware.compression.CompressionOptions;

app.use(Expressify.compression(
    CompressionOptions.builder()
        .threshold(1024)         // only compress if body > 1 KB (default: 1 KB)
        .level(6)                // compression level 1-9 (default: 6)
        .filter((req, res) -> {  // custom filter — return true to compress
            String ct = res.get("Content-Type");
            return ct != null && ct.contains("json");  // only compress JSON
        })
        .build()
));
```

### Options

| Option | Type | Default | Description |
|---|---|---|---|
| `threshold(int)` | bytes | 1024 | Minimum size to compress |
| `level(int)` | 1-9 | 6 | Compression level (1=fast, 9=best) |
| `filter(fn)` | BiFunction | compress all | Custom filter function |

---

## Threshold — minimum size

Small responses are not worth compressing (the compressed version might be larger):

```java
CompressionOptions.builder()
    .threshold(2048)   // only compress if body is > 2 KB
    .build()
```

Set to `0` to compress everything:

```java
.threshold(0)
```

---

## Skip compression on specific routes

```java
// Global compression
app.use(Expressify.compression());

// On a specific route, disable it
app.get("/stream", (req, res) -> {
    res.header("Cache-Control", "no-transform");  // tells proxy/browser not to compress
    res.send(largeBody);
});
```

Or use the filter option:

```java
app.use(Expressify.compression(
    CompressionOptions.builder()
        .filter((req, res) -> !req.path().startsWith("/no-compress"))
        .build()
));
```

---

## Checking if compression worked

In the browser's developer tools (Network tab), look at the response headers:
- `Content-Encoding: gzip` → response is compressed ✅
- No `Content-Encoding` header → not compressed

---

## Typical production setup

```java
// Put compression BEFORE your routes and other middleware
// (needs to wrap the response before it's sent)
app.use(Expressify.compression());
app.use(Expressify.json());
app.use(Expressify.cors());
// ...your routes
```

---

## Next steps

- [17 — Session](17_session.md)
- [04 — Middleware](04_middleware.md)
