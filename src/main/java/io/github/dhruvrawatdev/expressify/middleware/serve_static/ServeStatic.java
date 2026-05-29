package io.github.dhruvrawatdev.expressify.middleware.serve_static;

import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.io.InputStream;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Static file middleware — mirrors {@code express.static()}.
 *
 * <pre>{@code
 * app.use(ServeStatic.serve("public"));
 * app.use("/assets", ServeStatic.serve("src/assets", StaticOptions.builder().maxAge(86400).build()));
 * }</pre>
 */
public class ServeStatic implements RouteHandler {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final String rootDir;
    private final StaticOptions opts;

    private ServeStatic(String rootDir, StaticOptions opts) {
        Path p = Path.of(rootDir);
        this.rootDir = p.isAbsolute() ? rootDir
                : Path.of(System.getProperty("user.dir"), rootDir).toString();
        this.opts = opts != null ? opts : StaticOptions.builder().build();
    }

    /**
     * Serve static files from the given root directory with default options.
     *
     * <p>Handles {@code GET} and {@code HEAD} requests only. Resolves files relative to
     * {@code root}, serves {@code index.html} for directory requests, enforces dotfile
     * blocking (ignore policy), and sets no browser cache ({@code Cache-Control: public, max-age=0}).
     *
     * <pre>{@code
     * app.use(ServeStatic.serve("public"));
     * // Relative paths are resolved from the JVM working directory (project root)
     * }</pre>
     *
     * @param root directory path (absolute or relative to the JVM working directory)
     *             from which files are served
     * @return a configured {@link ServeStatic} handler ready to be passed to {@code app.use()}
     */
    public static ServeStatic serve(String root) {
        return new ServeStatic(root, null);
    }

    /**
     * Serve static files from the given root directory with custom options.
     *
     * <pre>{@code
     * app.use("/assets", ServeStatic.serve("src/assets", StaticOptions.builder()
     *     .maxAge(86400)       // 1-day browser cache
     *     .dotfiles("deny")    // reject dot-file requests with 403
     *     .index("index.html")
     *     .build()));
     * }</pre>
     *
     * @param root directory path (absolute or relative) from which files are served
     * @param opts static-file configuration; build with {@link StaticOptions#builder()}
     * @return a configured {@link ServeStatic} handler ready to be passed to {@code app.use()}
     */
    public static ServeStatic serve(String root, StaticOptions opts) {
        return new ServeStatic(root, opts);
    }

    @Override
    public void handle(Request req, Response res, NextFunction next) throws Exception {
        if (!"GET".equals(req.method()) && !"HEAD".equals(req.method())) {
            next.run();
            return;
        }

        String requestPath;
        try {
            requestPath = URLDecoder.decode(req.getRoutingPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            next.run();
            return;
        }

        if (requestPath.startsWith("/")) requestPath = requestPath.substring(1);

        // Dotfile handling (checked before path resolution)
        if (hasDotSegment(requestPath)) {
            String policy = opts.getDotfiles();
            if ("deny".equals(policy)) { res.status(403).send("Forbidden"); return; }
            if ("ignore".equals(policy)) { next.run(); return; }
        }

        Path rootNorm = Path.of(rootDir).toAbsolutePath().normalize();
        Path file = rootNorm.resolve(requestPath).normalize();

        // Security: reject any path that escapes the root directory
        if (!file.startsWith(rootNorm)) { next.run(); return; }

        if (Files.isDirectory(file)) {
            String idx = opts.getIndex();
            if (idx != null && !idx.isBlank()) {
                file = file.resolve(idx);
            } else if (opts.isRedirect() && !requestPath.endsWith("/")) {
                res.redirect(req.path() + "/");
                return;
            }
        }

        if (!Files.exists(file) || Files.isDirectory(file)) {
            next.run();
            return;
        }

        long lastModifiedMs = Files.getLastModifiedTime(file).toMillis();
        long fileSize       = Files.size(file);

        // ETag: weak tag based on size + last-modified (mirrors express.static default)
        String etag = null;
        if (opts.isEtag()) {
            etag = "W/\"" + fileSize + "-" + lastModifiedMs + "\"";
            String inm = req.get("If-None-Match");
            if (etag.equals(inm)) { res.status(304).end(); return; }
        }

        // If-Modified-Since
        if (opts.isLastModified()) {
            String ims = req.get("If-Modified-Since");
            if (ims != null) {
                try {
                    Instant clientTime = Instant.from(HTTP_DATE.parse(ims));
                    if (lastModifiedMs / 1000 <= clientTime.getEpochSecond()) {
                        res.status(304).end();
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }

        String mime = guessMime(file.toString());

        res.status(200)
           .type(mime)
           .header("Content-Length", String.valueOf(fileSize));

        if (etag != null) {
            res.header("ETag", etag);
        }
        if (opts.isLastModified()) {
            res.header("Last-Modified", HTTP_DATE.format(Instant.ofEpochMilli(lastModifiedMs)));
        }
        if (opts.getMaxAge() >= 0) {
            res.header("Cache-Control", "public, max-age=" + opts.getMaxAge());
        }

        if ("HEAD".equals(req.method())) { res.end(); return; }

        try (InputStream in = Files.newInputStream(file)) {
            res.pipe(in);
        }
    }

    private static boolean hasDotSegment(String path) {
        for (String seg : path.split("/")) {
            if (seg.startsWith(".") && seg.length() > 1) return true;
        }
        return false;
    }

    private static String guessMime(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".xml")) return "application/xml; charset=utf-8";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".zip")) return "application/zip";
        String probed = URLConnection.guessContentTypeFromName(path);
        return probed != null ? probed : "application/octet-stream";
    }
}
