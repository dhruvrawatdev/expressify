# 23 — Serve Static Files

The static file middleware serves files (HTML, CSS, JavaScript, images) directly from a folder on disk.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_static.ServeStatic;

// Serve all files in the "public" folder
app.use(ServeStatic.create("public"));
```

If you have a file at `public/style.css`, it's now available at `http://localhost:3000/style.css`.

---

## Directory structure example

```
public/
├── index.html
├── style.css
├── app.js
└── images/
    ├── logo.png
    └── banner.jpg
```

After `app.use(ServeStatic.create("public"))`:
- `GET /index.html` → serves `public/index.html`
- `GET /style.css` → serves `public/style.css`
- `GET /images/logo.png` → serves `public/images/logo.png`

---

## Mount at a URL prefix

```java
// Files in "public/assets" are served at /assets/*
app.use("/assets", ServeStatic.create("public/assets"));
```

Now `public/assets/logo.png` is at `http://localhost:3000/assets/logo.png`.

---

## Options

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_static.StaticOptions;

app.use(ServeStatic.create("public",
    StaticOptions.builder()
        .etag(true)              // enable ETag caching (default: true)
        .maxAge(86400)           // browser cache: 1 day in seconds
        .index("index.html")     // serve this file for directory requests
        .dotFiles("deny")        // "allow", "deny", or "ignore" .hidden files
        .redirect(true)          // redirect /dir to /dir/ for directory requests
        .lastModified(true)      // set Last-Modified header
        .build()
));
```

| Option | Default | Description |
|---|---|---|
| `etag(boolean)` | `true` | ETag-based caching |
| `maxAge(int)` | 0 | Browser cache duration in seconds |
| `index(String)` | `"index.html"` | Index file for directory requests |
| `dotFiles(String)` | `"ignore"` | Handling of `.hidden` files |
| `redirect(boolean)` | `true` | Redirect to trailing slash for directories |
| `lastModified(boolean)` | `true` | Set Last-Modified header |

---

## Multiple static directories

```java
// Check "public" first, then "uploads"
app.use(ServeStatic.create("public"));
app.use(ServeStatic.create("uploads"));
```

---

## Caching for production

```java
app.use(ServeStatic.create("public",
    StaticOptions.builder()
        .maxAge(365 * 24 * 3600)  // 1 year cache for versioned assets
        .etag(true)
        .build()
));
```

---

## Serving an SPA (Single Page Application)

For React, Vue, Angular apps where the frontend handles routing:

```java
// Static assets
app.use(ServeStatic.create("dist"));

// API routes
app.use("/api", apiRouter);

// All other routes → serve index.html (SPA handles routing)
app.get("/*", (req, res) -> {
    res.sendFile("dist/index.html");
});
```

---

## Next steps

- [24 — Serve Index](24_serve_index.md)
- [25 — Serve Favicon](25_serve_favicon.md)
