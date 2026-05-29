package io.github.dhruvrawatdev.expressify.middleware.method_override;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.Set;

/**
 * HTTP method override middleware — port of the Node.js {@code method-override} package.
 *
 * <p>Allows clients to send a desired HTTP method via a header or query string when
 * the transport method is restricted (e.g. HTML forms only support GET/POST).
 *
 * <pre>{@code
 * // Default: read X-HTTP-Method-Override header, only for POST requests
 * app.use(MethodOverride.create());
 *
 * // Custom header
 * app.use(MethodOverride.create("X-Method-Override"));
 *
 * // Query string: ?_method=DELETE
 * app.use(MethodOverride.create("_method"));
 *
 * // Custom options — allow override on any method
 * app.use(MethodOverride.create("X-HTTP-Method-Override",
 *     MethodOverrideOptions.builder().methods(null).build()));
 * }</pre>
 */
public class MethodOverride {

    private static final Set<String> VALID_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
    );

    private MethodOverride() {}

    /**
     * Override the HTTP method using the {@code X-HTTP-Method-Override} header, POST requests only.
     *
     * <pre>{@code
     * app.use(MethodOverride.create());
     * // HTML form can now send DELETE: <input type="hidden" name="_method" value="DELETE">
     * }</pre>
     *
     * @return a {@link RouteHandler} that reads {@code X-HTTP-Method-Override} and rewrites
     *         the request method for downstream routing
     */
    public static RouteHandler create() {
        return create("X-HTTP-Method-Override", MethodOverrideOptions.defaults());
    }

    /**
     * Override the HTTP method using the given header name or query parameter, POST requests only.
     *
     * <p>If {@code getter} starts with {@code "X-"} it is treated as a header name;
     * otherwise it is treated as a query parameter name (e.g., {@code "_method"} for
     * {@code ?_method=DELETE}).
     *
     * @param getter header name (e.g., {@code "X-Method-Override"}) or query key
     *               (e.g., {@code "_method"})
     * @return a {@link RouteHandler} that rewrites the request method for downstream routing
     */
    public static RouteHandler create(String getter) {
        return create(getter, MethodOverrideOptions.defaults());
    }

    /**
     * Override the HTTP method using the given header or query key with custom options.
     *
     * <pre>{@code
     * // Allow override on any incoming method (not just POST)
     * app.use(MethodOverride.create("X-HTTP-Method-Override",
     *     MethodOverrideOptions.builder().methods(null).build()));
     * }</pre>
     *
     * @param getter  header name (starts with {@code "X-"}) or query parameter name
     * @param options override options; build with {@link MethodOverrideOptions#builder()}
     * @return a {@link RouteHandler} that conditionally rewrites the request method
     */
    public static RouteHandler create(String getter, MethodOverrideOptions options) {
        boolean isHeader = getter.toUpperCase().startsWith("X-");
        return (req, res, next) -> {
            if (options.getMethods() != null
                    && !options.getMethods().contains(req.originalMethod())) {
                next.run();
                return;
            }

            String val;
            if (isHeader) {
                res.vary(getter);
                String hdr = req.get(getter);
                if (hdr != null && !hdr.isBlank()) {
                    int comma = hdr.indexOf(',');
                    val = comma != -1 ? hdr.substring(0, comma).trim() : hdr.trim();
                } else {
                    val = null;
                }
            } else {
                val = req.query(getter);
            }

            if (val != null && !val.isBlank()) {
                String upper = val.toUpperCase();
                if (VALID_METHODS.contains(upper)) {
                    req.setMethodOverride(upper);
                }
            }

            next.run();
        };
    }
}
