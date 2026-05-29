package io.github.dhruvrawatdev.expressify.middleware.serve_favicon;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Favicon middleware — port of the Node.js {@code serve-favicon} npm package.
 *
 * <p>Intercepts requests for {@code /favicon.ico}, serves the icon from an in-memory
 * byte array with ETag validation, and sets proper caching headers. All other paths
 * are passed through untouched.
 *
 * <h2>Why use this?</h2>
 * <p>Without this middleware, every page load causes an additional server round-trip
 * for the browser's automatic {@code /favicon.ico} request. This middleware responds
 * instantly from memory and instructs browsers to cache the icon for up to one day
 * (configurable), eliminating that overhead.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Minimal — load favicon.ico from the public directory
 * app.use(ServeFavicon.use("public/favicon.ico"));
 *
 * // Custom cache duration (30 days)
 * app.use(ServeFavicon.use("public/favicon.ico",
 *     FaviconOptions.builder().maxAge(2592000).build()));
 *
 * // From a byte array (e.g. loaded from a resource stream at startup)
 * byte[] icon = getClass().getResourceAsStream("/favicon.ico").readAllBytes();
 * app.use(ServeFavicon.use(icon));
 * }</pre>
 *
 * <h2>Method support</h2>
 * <ul>
 *   <li>{@code GET} — returns the icon bytes with caching headers</li>
 *   <li>{@code HEAD} — returns headers only, no body</li>
 *   <li>{@code OPTIONS} — returns {@code Allow: GET, HEAD, OPTIONS}</li>
 *   <li>All other methods — {@code 405 Method Not Allowed}</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * <p>Responses include {@code ETag} and {@code Cache-Control} headers. Clients that
 * already hold the icon receive a lightweight {@code 304 Not Modified} with no body.
 */
public final class ServeFavicon {

    private ServeFavicon() {}

    /**
     * Serve the favicon at {@code path} with a 1-day browser cache.
     *
     * <p>The file is read once at startup and kept in memory. Relative paths are
     * resolved from the JVM working directory (typically the project root).
     *
     * <pre>{@code app.use(ServeFavicon.use("public/favicon.ico")); }</pre>
     *
     * @param path absolute or project-relative path to the favicon file (ICO, PNG, SVG, etc.)
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico} requests
     * @throws IllegalArgumentException if the path does not exist or points to a directory
     * @throws RuntimeException         if the file cannot be read
     */
    public static RouteHandler use(String path) {
        return use(path, FaviconOptions.defaults());
    }

    /**
     * Serve the favicon at {@code path} with custom options.
     *
     * <pre>{@code
     * app.use(ServeFavicon.use("public/favicon.ico",
     *     FaviconOptions.builder().maxAge(2592000).build())); // 30-day cache
     * }</pre>
     *
     * @param path absolute or project-relative path to the favicon file
     * @param opts favicon options; build with {@link FaviconOptions#builder()}
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico} requests
     * @throws IllegalArgumentException if the file does not exist or is a directory
     */
    public static RouteHandler use(String path, FaviconOptions opts) {
        if (path == null) throw new IllegalArgumentException("path must not be null");
        Path p = Path.of(path);
        if (!p.isAbsolute()) {
            p = Path.of(System.getProperty("user.dir")).resolve(path).normalize();
        }
        if (!Files.exists(p))      throw new IllegalArgumentException("Favicon not found: " + path);
        if (Files.isDirectory(p))  throw new IllegalArgumentException("Favicon path is a directory: " + path);
        byte[] icon;
        try {
            icon = Files.readAllBytes(p);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read favicon: " + path, e);
        }
        return buildHandler(icon, opts != null ? opts : FaviconOptions.defaults());
    }

    /**
     * Serve the favicon from a raw byte array with a 1-day browser cache.
     *
     * <p>Useful when the favicon is embedded in your JAR as a resource:
     * <pre>{@code
     * byte[] icon = MyApp.class.getResourceAsStream("/favicon.ico").readAllBytes();
     * app.use(ServeFavicon.use(icon));
     * }</pre>
     *
     * @param iconData raw bytes of the favicon file
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico} requests
     * @throws IllegalArgumentException if {@code iconData} is {@code null} or empty
     */
    public static RouteHandler use(byte[] iconData) {
        return use(iconData, FaviconOptions.defaults());
    }

    /**
     * Serve the favicon from a raw byte array with custom options.
     *
     * @param iconData raw bytes of the favicon file
     * @param opts     favicon options; build with {@link FaviconOptions#builder()}
     * @return a {@link RouteHandler} that intercepts {@code GET /favicon.ico} requests
     * @throws IllegalArgumentException if {@code iconData} is {@code null}
     */
    public static RouteHandler use(byte[] iconData, FaviconOptions opts) {
        if (iconData == null) throw new IllegalArgumentException("iconData must not be null");
        return buildHandler(iconData.clone(), opts != null ? opts : FaviconOptions.defaults());
    }

    // Internal

    private static RouteHandler buildHandler(byte[] icon, FaviconOptions opts) {
        String etag         = computeETag(icon);
        String cacheControl = "public, max-age=" + opts.getMaxAge();
        String contentLen   = String.valueOf(icon.length);

        return (req, res, next) -> {
            if (!"/favicon.ico".equals(req.path())) {
                next.run();
                return;
            }

            String method = req.method();
            switch (method) {
                case "GET", "HEAD" -> { /* handled below */ }
                case "OPTIONS" -> {
                    res.set("Allow", "GET, HEAD, OPTIONS").status(200).end();
                    return;
                }
                default -> {
                    res.set("Allow", "GET, HEAD, OPTIONS").status(405).end();
                    return;
                }
            }

            // Conditional GET — skip body if client already has this version
            String ifNoneMatch = req.get("If-None-Match");
            if (etag.equals(ifNoneMatch)) {
                res.status(304).end();
                return;
            }

            res.set("Content-Type", "image/x-icon")
               .set("Cache-Control", cacheControl)
               .set("ETag", etag)
               .set("Content-Length", contentLen);

            if ("HEAD".equals(method)) {
                res.end();
                return;
            }
            res.send(icon);
        };
    }

    private static String computeETag(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return "W/\"" + data.length + "-" + Long.toHexString(crc.getValue()) + "\"";
    }
}
