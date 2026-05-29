package io.github.dhruvrawatdev.expressify.middleware.vhost;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Virtual host routing middleware — port of the Node.js {@code vhost} npm package.
 *
 * <p>Routes requests to different handler chains based on the request's {@code Host} header.
 * Supports exact hostname matching and wildcard subdomain patterns. Requests that do
 * not match the pattern fall through to the next middleware automatically.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Exact match — handle requests for www.example.com
 * app.use(VHost.use("www.example.com", (req, res) -> res.send("Main site")));
 *
 * // Wildcard subdomain — handle any *.example.com
 * app.use(VHost.use("*.example.com", (req, res, next) -> {
 *     VHostInfo vhost = (VHostInfo) req.locals().get("vhost");
 *     String subdomain = vhost.get(0);  // e.g. "api" for "api.example.com"
 *     res.send("Subdomain: " + subdomain);
 * }));
 *
 * // Multiple wildcards — "*.*.example.com"
 * app.use(VHost.use("*.*.example.com", (req, res) -> {
 *     VHostInfo vhost = (VHostInfo) req.locals().get("vhost");
 *     // vhost.get(0) → left-most segment, vhost.get(1) → second segment
 *     res.send(vhost.get(0) + "." + vhost.get(1));
 * }));
 *
 * // Route to a middleware stack
 * app.use(VHost.use("api.example.com",
 *     Expressify.json(),
 *     Expressify.cors(),
 *     (req, res) -> res.json(Map.of("version", "v1"))
 * ));
 * }</pre>
 *
 * <h2>VHostInfo — captured values</h2>
 * <p>When the pattern contains wildcards, matched segments are accessible through the
 * {@link VHostInfo} object stored at {@code req.locals().get("vhost")}:
 * <pre>{@code
 * VHostInfo vhost = (VHostInfo) req.locals().get("vhost");
 * vhost.hostname();   // full hostname, e.g. "api.example.com"
 * vhost.get(0);       // first wildcard capture, e.g. "api"
 * vhost.length();     // number of wildcard captures
 * }</pre>
 *
 * <h2>Pattern rules</h2>
 * <ul>
 *   <li>{@code *} matches any non-empty sequence of characters that does not contain a dot</li>
 *   <li>Matching is case-insensitive</li>
 *   <li>Port is stripped from the {@code Host} header before matching</li>
 * </ul>
 */
public final class VHost {

    private VHost() {}

    /**
     * Route requests whose {@code Host} hostname matches {@code pattern} to the given handlers.
     * Unmatched requests call {@code next()} and continue down the stack.
     *
     * <p>Wildcard {@code *} in the pattern captures one label (dot-delimited segment)
     * of the hostname. The captured values are stored as a {@link VHostInfo} in
     * {@code req.locals().get("vhost")}.
     *
     * <pre>{@code
     * // Exact — handle "admin.example.com" only
     * app.use(VHost.use("admin.example.com", adminAuthMw, adminRouter));
     *
     * // Wildcard — handle any subdomain of example.com
     * app.use(VHost.use("*.example.com", (req, res, next) -> {
     *     VHostInfo v = (VHostInfo) req.locals().get("vhost");
     *     System.out.println("subdomain = " + v.get(0));
     *     next.run();
     * }, mainHandler));
     * }</pre>
     *
     * @param pattern  hostname pattern — exact or with {@code *} wildcards
     *                 (e.g. {@code "example.com"}, {@code "*.example.com"})
     * @param handlers one or more {@link RouteHandler}s executed in order when the hostname matches;
     *                 each must call {@code next.run()} except the final handler
     * @return a {@link RouteHandler} that performs virtual-host routing
     * @throws IllegalArgumentException if {@code pattern} is blank or no handlers are provided
     */
    public static RouteHandler use(String pattern, RouteHandler... handlers) {
        if (pattern == null || pattern.isBlank())
            throw new IllegalArgumentException("VHost pattern must not be blank");
        if (handlers == null || handlers.length == 0)
            throw new IllegalArgumentException("VHost requires at least one handler");

        Pattern regex = toPattern(pattern);
        List<RouteHandler> chain = Arrays.asList(handlers);

        return (req, res, next) -> {
            String host = extractHostname(req.get("Host"));
            if (host == null) { next.run(); return; }

            Matcher m = regex.matcher(host);
            if (!m.matches()) { next.run(); return; }

            List<String> captures = new ArrayList<>(m.groupCount());
            for (int i = 1; i <= m.groupCount(); i++) {
                String g = m.group(i);
                captures.add(g != null ? g : "");
            }
            req.locals().put("vhost", new VHostInfo(host, captures));

            runChain(chain, 0, req, res, next);
        };
    }

    /**
     * Route requests matching {@code pattern} to a list of handlers.
     * Convenience overload for programmatically assembled middleware stacks.
     *
     * <pre>{@code
     * List<RouteHandler> stack = List.of(authMw, logMw, handlerMw);
     * app.use(VHost.use("api.example.com", stack));
     * }</pre>
     *
     * @param pattern  hostname pattern
     * @param handlers ordered list of route handlers
     * @return a {@link RouteHandler} that performs virtual-host routing
     */
    public static RouteHandler use(String pattern, List<RouteHandler> handlers) {
        if (handlers == null || handlers.isEmpty())
            throw new IllegalArgumentException("VHost requires at least one handler");
        return use(pattern, handlers.toArray(new RouteHandler[0]));
    }

    // Internal

    private static Pattern toPattern(String hostname) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < hostname.length(); i++) {
            char c = hostname.charAt(i);
            if (c == '*') {
                sb.append("([^.]+)");
            } else if (c == '.') {
                sb.append("\\.");
            } else {
                // quote each literal char so regex special chars in hostnames are safe
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static String extractHostname(String host) {
        if (host == null || host.isBlank()) return null;
        // Handle IPv6: "[::1]:8080" → strip port after the closing bracket
        int bracket = host.lastIndexOf(']');
        int colon   = host.indexOf(':', bracket + 1);
        return colon != -1 ? host.substring(0, colon) : host;
    }

    private static void runChain(List<RouteHandler> handlers, int idx,
                                  Request req, Response res, NextFunction outer) throws Exception {
        if (idx >= handlers.size()) {
            outer.run();
            return;
        }
        handlers.get(idx).handle(req, res, new NextFunction() {
            @Override public void run() throws Exception   { runChain(handlers, idx + 1, req, res, outer); }
            @Override public void error(Throwable e) throws Exception { outer.error(e); }
        });
    }
}
