# 24 — Serve Index (Directory Listing)

Serve Index generates an HTML page that lists the files in a directory — like a file browser. Useful for download servers or internal tools.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_index.ServeIndex;

// List files in the "files" directory at /files
app.use("/files", ServeIndex.create("files"));
```

When you visit `http://localhost:3000/files`, you see a list of files you can download.

---

## Combine with Serve Static

Usually you want both: list files AND download them.

```java
// Serve static files (downloads)
app.use("/files", ServeStatic.create("files"));

// Serve directory listing
app.use("/files", ServeIndex.create("files"));
```

Now users can:
- Browse the directory listing at `/files`
- Click a file to download it

---

## Options

```java
import io.github.dhruvrawatdev.expressify.middleware.serve_index.ServeIndexOptions;

app.use("/files", ServeIndex.create("files",
    ServeIndexOptions.builder()
        .view("tiles")          // "tiles" (icon view) or "details" (table view)
        .hidden(false)          // show hidden files (default: false)
        .icons(true)            // show file type icons (default: true)
        .filter((filename, index, dir, stat) -> {
            // Only show .pdf files
            return filename.endsWith(".pdf");
        })
        .build()
));
```

| Option | Default | Description |
|---|---|---|
| `view(String)` | `"tiles"` | `"tiles"` or `"details"` |
| `hidden(boolean)` | `false` | Show dotfiles |
| `icons(boolean)` | `true` | Show file type icons |
| `filter(fn)` | none | Filter which files are listed |

---

## Next steps

- [23 — Serve Static](23_serve_static.md)
- [25 — Serve Favicon](25_serve_favicon.md)
