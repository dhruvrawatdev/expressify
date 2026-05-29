package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Body-parsing middleware — mirrors Express.js body-parser.
 *
 * <pre>{@code
 * app.use(BodyParser.json());
 * app.use(BodyParser.urlencoded(UrlencodedOptions.builder().extended(true).build()));
 * app.use(BodyParser.text());
 * app.use(BodyParser.raw());
 * }</pre>
 */
public final class BodyParser {

    private BodyParser() {}

    /**
     * Parse JSON request bodies with default options.
     *
     * <p>Only processes requests whose {@code Content-Type} matches {@code application/json}.
     * Default limit: 100 KB. Decompresses {@code gzip} / {@code deflate} bodies automatically.
     * After this middleware runs, call {@code req.bodyAs(MyClass.class)} or {@code req.body()}
     * to access the parsed payload.
     *
     * @return a {@link RouteHandler} that parses JSON bodies and stores them on the request
     */
    public static RouteHandler json() {
        return json(JsonOptions.builder().build());
    }

    /**
     * Parse JSON request bodies with custom options.
     *
     * <pre>{@code
     * app.use(BodyParser.json(JsonOptions.builder()
     *     .limit(5 * 1024 * 1024)   // 5 MB max body
     *     .strict(false)             // also accept primitives at the top level
     *     .build()));
     * }</pre>
     *
     * @param opts JSON parser configuration; build with {@link JsonOptions#builder()}
     * @return a {@link RouteHandler} that parses JSON bodies according to the given options
     */
    public static RouteHandler json(JsonOptions opts) {
        return (req, res, next) -> {
            String ct = req.get("Content-Type");
            if (ct == null || !matchesAnyType(ct, new String[]{opts.getType()})) {
                next.run();
                return;
            }
            // handleInflate checks Content-Length, decompresses if needed, and reads body
            if (!handleInflate(req, res, opts.isInflate(), opts.getLimit(),
                    opts.getLimit() + " bytes")) return;
            next.run();
        };
    }

    /**
     * Parse URL-encoded ({@code application/x-www-form-urlencoded}) request bodies with default options.
     *
     * <p>Default limit: 100 KB, up to 1 000 parameters. Use {@code req.body()} or
     * {@code req.formParam("fieldName")} to access parsed form fields after this middleware runs.
     *
     * @return a {@link RouteHandler} that parses URL-encoded form bodies
     */
    public static RouteHandler urlencoded() {
        return urlencoded(UrlencodedOptions.builder().build());
    }

    /**
     * Parse URL-encoded request bodies with custom options.
     *
     * <pre>{@code
     * app.use(BodyParser.urlencoded(UrlencodedOptions.builder()
     *     .extended(true)    // parse nested objects like user[name]=Alice
     *     .limit("2mb")
     *     .build()));
     * }</pre>
     *
     * @param opts URL-encoded parser configuration; build with {@link UrlencodedOptions#builder()}
     * @return a {@link RouteHandler} that parses form bodies according to the given options
     */
    public static RouteHandler urlencoded(UrlencodedOptions opts) {
        long maxBytes = parseSize(opts.getLimit());
        return (req, res, next) -> {
            String ct = req.get("Content-Type");
            if (ct == null || !ct.toLowerCase().contains(opts.getType().toLowerCase())) {
                next.run();
                return;
            }
            enforceLimit(req, res, maxBytes, opts.getLimit());
            if (res.isCommitted()) return;
            req.body(); // ensure body bytes are read
            next.run();
        };
    }

    /**
     * Parse plain-text ({@code text/plain}) request bodies with default options (UTF-8, 100 KB).
     *
     * <p>The raw string is available via {@code req.bodyText()} after this middleware runs.
     *
     * @return a {@link RouteHandler} that reads and stores plain-text bodies
     */
    public static RouteHandler text() {
        return text(TextOptions.builder().build());
    }

    public static RouteHandler text(TextOptions opts) {
        long maxBytes = parseSize(opts.getLimit());
        return (req, res, next) -> {
            String ct = req.get("Content-Type");
            if (ct == null || !matchesAnyType(ct, opts.getTypes())) {
                next.run();
                return;
            }
            if (!handleInflate(req, res, opts.isInflate(), maxBytes, opts.getLimit())) return;

            byte[] raw = req.getRawBody();
            Charset cs = charsetFromContentType(ct, opts.getDefaultCharset());

            if (opts.getVerify() != null) {
                try { opts.getVerify().verify(req, res, raw, cs.name()); }
                catch (Exception e) { next.error(e); return; }
            }

            req.setTextCharset(cs.name());
            req.setTextParsed(true);
            next.run();
        };
    }

    /**
     * Parse raw binary ({@code application/octet-stream}) request bodies with default options (100 KB).
     *
     * <p>The raw bytes are available via {@code req.getRawBody()} after this middleware runs.
     *
     * @return a {@link RouteHandler} that reads and stores binary bodies
     */
    public static RouteHandler raw() {
        return raw(RawOptions.builder().build());
    }

    public static RouteHandler raw(RawOptions opts) {
        long maxBytes = parseSize(opts.getLimit());
        return (req, res, next) -> {
            String ct = req.get("Content-Type");
            if (ct == null || !matchesAnyType(ct, opts.getTypes())) {
                next.run();
                return;
            }
            if (!handleInflate(req, res, opts.isInflate(), maxBytes, opts.getLimit())) return;

            byte[] raw = req.getRawBody();

            if (opts.getVerify() != null) {
                try { opts.getVerify().verify(req, res, raw, "binary"); }
                catch (Exception e) { next.error(e); return; }
            }

            req.setRawParsed(true);
            next.run();
        };
    }

    //  helpers

    static boolean matchesAnyType(String contentType, String[] patterns) {
        String ctBase = contentType.split(";")[0].trim().toLowerCase();
        for (String pattern : patterns) {
            String p = pattern.trim().toLowerCase();
            if (p.equals("*/*") || p.equals("*")) return true;
            if (p.endsWith("/*")) {
                if (ctBase.startsWith(p.substring(0, p.length() - 2) + "/")) return true;
            } else {
                if (ctBase.equals(p)) return true;
            }
        }
        return false;
    }

    private static boolean handleInflate(Request req, Response res,
                                         boolean inflate, long maxBytes, String limitStr)
            throws Exception {
        String encoding = req.get("Content-Encoding");
        boolean compressed = encoding != null &&
                (encoding.equalsIgnoreCase("gzip") || encoding.equalsIgnoreCase("deflate"));
        if (compressed && !inflate) {
            res.status(415).json(java.util.Map.of("error", "Unsupported Media Type"));
            return false;
        }
        String cl = req.get("Content-Length");
        if (cl != null) {
            try {
                long len = Long.parseLong(cl.trim());
                if (len > maxBytes) {
                    res.status(413).json(java.util.Map.of("error", "Payload Too Large",
                            "limit", limitStr));
                    return false;
                }
            } catch (NumberFormatException ignored) {}
        }
        req.body(); // read into cache
        if (compressed) {
            byte[] decompressed = decompress(req.getRawBody(), encoding);
            if (decompressed == null) {
                res.status(400).json(java.util.Map.of("error", "Bad Request",
                        "message", "Failed to decompress body"));
                return false;
            }
            if (decompressed.length > maxBytes) {
                res.status(413).json(java.util.Map.of("error", "Payload Too Large",
                        "limit", limitStr));
                return false;
            }
            req.overrideBodyBytes(decompressed);
        }
        return true;
    }

    private static void enforceLimit(Request req, Response res, long maxBytes, String limitStr)
            throws Exception {
        String cl = req.get("Content-Length");
        if (cl != null) {
            try {
                long len = Long.parseLong(cl.trim());
                if (len > maxBytes) {
                    res.status(413).json(java.util.Map.of("error", "Payload Too Large",
                            "limit", limitStr));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    static byte[] decompress(byte[] data, String encoding) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            InputStream in;
            if (encoding.equalsIgnoreCase("gzip")) {
                in = new GZIPInputStream(bais);
            } else {
                try {
                    in = new InflaterInputStream(bais);
                    byte[] r = in.readAllBytes();
                    in.close();
                    return r;
                } catch (Exception e) {
                    bais.reset();
                    in = new InflaterInputStream(bais, new Inflater(true));
                }
            }
            byte[] result = in.readAllBytes();
            in.close();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public static long parseSize(String size) {
        String s = size.trim().toLowerCase();
        try {
            if (s.endsWith("gb")) return Long.parseLong(s.substring(0, s.length() - 2).trim()) * 1024L * 1024L * 1024L;
            if (s.endsWith("mb")) return Long.parseLong(s.substring(0, s.length() - 2).trim()) * 1024L * 1024L;
            if (s.endsWith("kb")) return Long.parseLong(s.substring(0, s.length() - 2).trim()) * 1024L;
            if (s.endsWith("b"))  return Long.parseLong(s.substring(0, s.length() - 1).trim());
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid body size: '" + size + "'", e);
        }
    }

    private static Charset charsetFromContentType(String ct, String defaultCs) {
        for (String part : ct.split(";")) {
            String p = part.trim();
            if (p.toLowerCase().startsWith("charset=")) {
                try { return Charset.forName(p.substring(8).trim()); }
                catch (Exception ignored) {}
            }
        }
        return Charset.forName(defaultCs);
    }
}
