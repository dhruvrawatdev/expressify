package io.github.dhruvrawatdev.expressify.middleware.serve_index;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Directory listing middleware — port of the Node.js {@code serve-index} npm package.
 *
 * <p>Generates a browsable directory listing when a request path points to a directory
 * within the configured root. Files (not directories) pass through to the next handler,
 * allowing this middleware to be composed with {@code ServeStatic} for a complete
 * file-server setup.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Directory listing for all directories under "public/"
 * app.use(ServeStatic.serve("public"));   // serve actual files first
 * app.use(ServeIndex.directory("public")); // then list directories
 *
 * // Mount under a URL prefix
 * app.use("/files", ServeStatic.serve("uploads"));
 * app.use("/files", ServeIndex.directory("uploads",
 *     ServeIndexOptions.builder()
 *         .hidden(true)                          // show dot-files
 *         .filter(name -> !name.endsWith(".tmp")) // hide temp files
 *         .build()));
 * }</pre>
 *
 * <h2>Content negotiation</h2>
 * <p>Response format is chosen based on the request's {@code Accept} header:
 * <ul>
 *   <li>{@code text/html} (default) — browsable HTML table with breadcrumbs</li>
 *   <li>{@code application/json} — JSON array of file-entry objects</li>
 *   <li>{@code text/plain} — newline-separated list of filenames</li>
 * </ul>
 *
 * <h2>JSON entry format</h2>
 * <pre>{@code
 * [
 *   { "name": "index.html", "type": "file",      "size": 1024,  "lastModified": "2026-01-01T00:00:00Z" },
 *   { "name": "assets",     "type": "directory",  "size": 0,     "lastModified": "2026-01-01T00:00:00Z" }
 * ]
 * }</pre>
 *
 * <h2>Method support</h2>
 * <p>Only {@code GET} and {@code HEAD} are handled; all other methods fall through via {@code next()}.
 */
public final class ServeIndex {

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private ServeIndex() {}

    /**
     * Serve directory listings from {@code root} with default options.
     *
     * <p>Hidden files (names starting with {@code .}) are excluded by default.
     * Relative paths are resolved from the JVM working directory.
     *
     * <pre>{@code
     * app.use(ServeStatic.serve("public"));
     * app.use(ServeIndex.directory("public"));
     * }</pre>
     *
     * @param root directory path (absolute or relative to the JVM working directory)
     *             that will be used as the listing root
     * @return a {@link RouteHandler} that generates directory listings
     * @throws IllegalArgumentException if {@code root} does not exist or is not a directory
     */
    public static RouteHandler directory(String root) {
        return directory(root, ServeIndexOptions.defaults());
    }

    /**
     * Serve directory listings from {@code root} with custom options.
     *
     * <pre>{@code
     * app.use(ServeIndex.directory("uploads", ServeIndexOptions.builder()
     *     .hidden(true)
     *     .filter(name -> !name.startsWith("_"))
     *     .build()));
     * }</pre>
     *
     * @param root directory path (absolute or relative) used as the listing root
     * @param opts listing options; build with {@link ServeIndexOptions#builder()}
     * @return a {@link RouteHandler} that generates directory listings
     * @throws IllegalArgumentException if {@code root} does not exist or is not a directory
     */
    public static RouteHandler directory(String root, ServeIndexOptions opts) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        Path rootPath = Path.of(root);
        if (!rootPath.isAbsolute()) {
            rootPath = Path.of(System.getProperty("user.dir")).resolve(root).normalize();
        }
        if (!Files.exists(rootPath))     throw new IllegalArgumentException("ServeIndex root not found: " + root);
        if (!Files.isDirectory(rootPath)) throw new IllegalArgumentException("ServeIndex root is not a directory: " + root);

        Path   finalRoot = rootPath;
        ServeIndexOptions o = opts != null ? opts : ServeIndexOptions.defaults();

        return (req, res, next) -> {
            String method = req.method();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                next.run();
                return;
            }

            // Resolve request path to a filesystem directory
            String reqPath;
            try {
                reqPath = URLDecoder.decode(req.getRoutingPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                next.run();
                return;
            }
            if (reqPath.startsWith("/")) reqPath = reqPath.substring(1);

            Path target = finalRoot.resolve(reqPath).normalize();

            // Security: reject path traversal
            if (!target.startsWith(finalRoot)) {
                res.status(403).send("Forbidden");
                return;
            }

            if (!Files.exists(target) || !Files.isDirectory(target)) {
                next.run();
                return;
            }

            // Build the file listing
            List<FileEntry> entries = buildEntries(target, o);

            // Content negotiation
            String accept = req.get("Accept");
            if (accept != null && accept.contains("application/json")) {
                List<Map<String, Object>> json = new ArrayList<>(entries.size());
                for (FileEntry e : entries) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name",         e.name);
                    m.put("type",         e.dir ? "directory" : "file");
                    m.put("size",         e.size);
                    m.put("lastModified", e.lastModified);
                    json.add(m);
                }
                res.json(json);
            } else if (accept != null && accept.contains("text/plain")) {
                StringBuilder sb = new StringBuilder();
                for (FileEntry e : entries) {
                    sb.append(e.name);
                    if (e.dir) sb.append('/');
                    sb.append('\n');
                }
                res.set("Content-Type", "text/plain; charset=utf-8").send(sb.toString());
            } else {
                // Default: HTML
                String urlPath = req.path();
                if (!urlPath.endsWith("/")) urlPath += "/";
                res.set("Content-Type", "text/html; charset=utf-8")
                   .send(renderHtml(urlPath, entries));
            }
        };
    }

    // Internal

    private static List<FileEntry> buildEntries(Path dir, ServeIndexOptions opts) {
        List<FileEntry> entries = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                String name = p.getFileName().toString();
                if (!opts.isHidden() && name.startsWith(".")) return;
                if (opts.getFilter() != null && !opts.getFilter().test(name)) return;
                boolean isDir = Files.isDirectory(p);
                long size = 0;
                String modified = "";
                try {
                    if (!isDir) size = Files.size(p);
                    Instant inst = Files.getLastModifiedTime(p).toInstant();
                    modified = DISPLAY_DATE.format(inst);
                } catch (IOException ignored) {}
                entries.add(new FileEntry(name, isDir, size, modified));
            });
        } catch (IOException ignored) {}

        // Directories first, then files; both groups sorted alphabetically
        entries.sort(Comparator
            .comparing((FileEntry e) -> !e.dir)
            .thenComparing(e -> e.name.toLowerCase()));

        return entries;
    }

    private static String renderHtml(String urlPath, List<FileEntry> entries) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("  <meta charset=\"utf-8\">\n")
          .append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("  <title>Index of ").append(escapeHtml(urlPath)).append("</title>\n")
          .append("  <style>\n")
          .append("    *{box-sizing:border-box}body{font-family:monospace;margin:0;padding:1.5em;")
          .append("background:#fafafa;color:#333}\n")
          .append("    h1{font-size:1.1em;border-bottom:1px solid #ddd;padding-bottom:.6em;margin-bottom:1em}\n")
          .append("    table{border-collapse:collapse;width:100%;background:#fff;border:1px solid #e0e0e0;")
          .append("border-radius:4px;overflow:hidden}\n")
          .append("    th{text-align:left;background:#f0f0f0;padding:8px 12px;font-size:.85em;")
          .append("border-bottom:2px solid #ddd;white-space:nowrap}\n")
          .append("    td{padding:6px 12px;font-size:.85em;border-bottom:1px solid #f0f0f0}\n")
          .append("    tr:last-child td{border-bottom:none}\n")
          .append("    tr:hover td{background:#f9f9ff}\n")
          .append("    a{text-decoration:none;color:#0066cc}a:hover{text-decoration:underline}\n")
          .append("    .dir{color:#555}.sz,.dt{color:#888;white-space:nowrap}\n")
          .append("    .ico{margin-right:4px}\n")
          .append("  </style>\n</head>\n<body>\n")
          .append("  <h1>Index of ").append(escapeHtml(urlPath)).append("</h1>\n")
          .append("  <table>\n")
          .append("    <thead><tr>")
          .append("<th>Name</th><th>Size</th><th>Last Modified</th>")
          .append("</tr></thead>\n    <tbody>\n");

        // Parent directory link (unless we're at root)
        if (!"/".equals(urlPath)) {
            sb.append("      <tr><td><a href=\"../\"><span class=\"ico\">&#x1F4C2;</span>../</a></td>")
              .append("<td class=\"sz\">-</td><td class=\"dt\">-</td></tr>\n");
        }

        for (FileEntry e : entries) {
            String encodedName = URLEncoder.encode(e.name, StandardCharsets.UTF_8)
                                           .replace("+", "%20");
            String displayName = escapeHtml(e.name);
            String href, icon, trailing;
            if (e.dir) {
                href = encodedName + "/";
                icon = "&#x1F4C1;"; // folder icon
                trailing = "/";
            } else {
                href = encodedName;
                icon = "&#x1F4C4;"; // file icon
                trailing = "";
            }
            String sizeStr = e.dir ? "-" : formatSize(e.size);
            sb.append("      <tr>")
              .append("<td><a href=\"").append(href).append("\">")
              .append("<span class=\"ico\">").append(icon).append("</span>")
              .append(displayName).append(trailing).append("</a></td>")
              .append("<td class=\"sz\">").append(escapeHtml(sizeStr)).append("</td>")
              .append("<td class=\"dt\">").append(escapeHtml(e.lastModified)).append("</td>")
              .append("</tr>\n");
        }

        sb.append("    </tbody>\n  </table>\n</body>\n</html>");
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final class FileEntry {
        final String name;
        final boolean dir;
        final long size;
        final String lastModified;

        FileEntry(String name, boolean dir, long size, String lastModified) {
            this.name = name;
            this.dir = dir;
            this.size = size;
            this.lastModified = lastModified;
        }
    }
}
