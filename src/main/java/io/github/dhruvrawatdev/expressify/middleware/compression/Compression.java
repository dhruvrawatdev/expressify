package io.github.dhruvrawatdev.expressify.middleware.compression;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Response compression middleware — gzip/deflate using Undertow's response buffering.
 * Mirrors the compression npm package for Express.js.
 *
 * <pre>{@code
 * app.use(Compression.defaults());
 *
 * app.use(Compression.configure(CompressionOptions.builder()
 *     .threshold(512)
 *     .level(9)
 *     .build()));
 * }</pre>
 */
public final class Compression {

    private Compression() {}

    /**
     * Enable gzip/deflate compression with sensible defaults.
     *
     * <p>Default settings: 1 KB minimum body threshold, compression level 6, compresses
     * {@code text/*}, {@code application/json}, {@code application/javascript},
     * {@code application/xml}, and {@code image/svg+xml} content types.
     * Skips compression for clients that do not advertise {@code Accept-Encoding}.
     *
     * @return a {@link RouteHandler} that compresses responses when the client supports it
     */
    public static RouteHandler defaults() {
        return configure(CompressionOptions.builder().build());
    }

    /**
     * Enable compression with custom options.
     *
     * <pre>{@code
     * app.use(Compression.configure(CompressionOptions.builder()
     *     .threshold(512)   // compress responses larger than 512 bytes
     *     .level(9)         // maximum compression ratio
     *     .filter((req, res) -> {
     *         String ct = res.getBufferedContentType();
     *         return ct != null && ct.startsWith("text/");
     *     })
     *     .build()));
     * }</pre>
     *
     * @param opts compression configuration; build with {@link CompressionOptions#builder()}
     * @return a {@link RouteHandler} that buffers, compresses, and flushes the response body
     */
    public static RouteHandler configure(CompressionOptions opts) {
        return (req, res, next) -> {
            String ae = req.get("Accept-Encoding");
            if (ae == null || ae.isBlank()) {
                next.run();
                return;
            }

            res.enableBuffering();
            next.run();

            byte[] body = res.getBufferedBody();
            if (body == null || body.length == 0) return;

            // Custom filter
            if (opts.getFilter() != null && !opts.getFilter().shouldCompress(req, res)) {
                res.flushBuffered(body, null);
                return;
            }

            // Default content-type filter
            if (!isCompressible(res.getBufferedContentType())) {
                res.flushBuffered(body, null);
                return;
            }

            // Below threshold — don't compress
            if (body.length < opts.getThreshold()) {
                res.flushBuffered(body, null);
                return;
            }

            // Encoding selection: gzip > deflate
            if (ae.contains("gzip")) {
                res.flushBuffered(gzip(body, opts.getLevel()), "gzip");
            } else if (ae.contains("deflate")) {
                res.flushBuffered(deflate(body, opts.getLevel()), "deflate");
            } else {
                res.flushBuffered(body, null);
            }
        };
    }

    private static byte[] gzip(byte[] data, int level) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gos = new GZIPOutputStream(baos, true) {
            { def.setLevel(level); }
        }) {
            gos.write(data);
            gos.finish();
        }
        return baos.toByteArray();
    }

    private static byte[] deflate(byte[] data, int level) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        Deflater deflater = new Deflater(level);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
            dos.finish();
        } finally {
            deflater.end();
        }
        return baos.toByteArray();
    }

    private static boolean isCompressible(String contentType) {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.startsWith("text/")
                || lower.contains("application/json")
                || lower.contains("application/javascript")
                || lower.contains("application/xml")
                || lower.contains("application/xhtml+xml")
                || lower.contains("image/svg+xml");
    }
}
