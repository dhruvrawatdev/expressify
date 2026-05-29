package io.github.dhruvrawatdev.expressify.router.registry;

import io.github.dhruvrawatdev.expressify.router.core.RouteEntry;
import io.github.dhruvrawatdev.expressify.router.handler.AsyncParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.ErrorHandler;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.ParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.dhruvrawatdev.expressify.router.internal.PathPattern;

import java.util.regex.Pattern;

/**
 * Maintains the ordered list of all routing entries and compiles path patterns.
 */
public class RouteRegistry {

    private final List<RouteEntry> entries = new ArrayList<>();
    private final Map<String, List<ParamHandler>> paramHandlers = new LinkedHashMap<>();

    public List<RouteEntry> getEntries() { return entries; }

    // Registration helpers

    /** Add a route (exact match). */
    public void addRoute(String method, String path, List<RouteHandler> handlers) {
        List<String> paramNames = new ArrayList<>();
        PathPattern pathPattern = PathPattern.compile(path, paramNames, false);
        entries.add(RouteEntry.builder()
                .type(RouteEntry.Type.ROUTE)
                .method(method != null ? method.toUpperCase() : null)
                .pathPattern(pathPattern)
                .paramNames(paramNames)
                .pathPrefix(path)
                .handlers(handlers)
                .build());
    }

    /** Add middleware (prefix match, optional path). */
    public void addMiddleware(String pathPrefix, List<RouteHandler> handlers) {
        PathPattern pathPattern = null;
        if (pathPrefix != null && !pathPrefix.isBlank() && !pathPrefix.equals("/")) {
            pathPattern = PathPattern.compile(pathPrefix, new ArrayList<>(), true);
        }
        entries.add(RouteEntry.builder()
                .type(RouteEntry.Type.MIDDLEWARE)
                .pathPattern(pathPattern)
                .pathPrefix(pathPrefix != null ? pathPrefix : "")
                .handlers(handlers)
                .build());
    }

    /** Mount a router at a path prefix. */
    public void addRouter(String pathPrefix, RouteEntry.RouterRef router) {
        PathPattern pathPattern = null;
        if (pathPrefix != null && !pathPrefix.isBlank() && !pathPrefix.equals("/")) {
            pathPattern = PathPattern.compile(pathPrefix, new ArrayList<>(), true);
        }
        entries.add(RouteEntry.builder()
                .type(RouteEntry.Type.ROUTER)
                .pathPattern(pathPattern)
                .pathPrefix(pathPrefix != null ? pathPrefix : "")
                .mountedRouter(router)
                .build());
    }

    /** Register the global error handler. */
    public void addErrorHandler(ErrorHandler handler) {
        entries.add(RouteEntry.builder()
                .type(RouteEntry.Type.ERROR_HANDLER)
                .errorHandler(handler)
                .build());
    }

    public void addParamHandler(String name, ParamHandler handler) {
        paramHandlers.computeIfAbsent(name, k -> new ArrayList<>()).add(handler);
    }

    public void addParamHandler(String name, AsyncParamHandler handler) {
        paramHandlers.computeIfAbsent(name, k -> new ArrayList<>()).add(wrapAsyncParam(handler));
    }

    public Map<String, List<ParamHandler>> getParamHandlers() { return paramHandlers; }

    private static ParamHandler wrapAsyncParam(AsyncParamHandler handler) {
        return (req, res, next, value) -> {
            boolean[] called = {false};
            NextFunction w = new NextFunction() {
                @Override public void run() throws Exception   { called[0] = true; next.run(); }
                @Override public void error(Throwable e) throws Exception { called[0] = true; next.error(e); }
            };
            java.util.concurrent.CompletableFuture<Void> f;
            try { f = handler.handle(req, res, w, value); } catch (Exception e) { next.error(e); return; }
            if (f == null) { if (!called[0]) next.run(); return; }
            try {
                f.join();
            } catch (java.util.concurrent.CompletionException e) {
                if (!called[0]) next.error(e.getCause() != null ? e.getCause() : e);
                return;
            }
            if (!called[0]) next.run();
        };
    }

    // Path compilation

    /**
     * Convert an Express-style path to a Java regex Pattern.
     *
     * <p>Supported syntax:
     * <ul>
     *   <li>{@code :name}   — named route parameter, e.g. {@code /users/:id}</li>
     *   <li>{@code *}       — wildcard, e.g. {@code /files/*}</li>
     *   <li>{@code x?}      — optional character, e.g. {@code /about?} matches /abou and /about</li>
     *   <li>{@code (group)?}— optional group, e.g. {@code /ab(cd)?e} matches /abe and /abcde</li>
     * </ul>
     *
     * @param path        Express-style path string
     * @param paramNames  Output list — named params are appended in order of appearance
     * @param prefix      If true, the pattern allows trailing path segments (for middleware prefix-matching)
     */
    public static Pattern compilePath(String path, List<String> paramNames, boolean prefix) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == ':') {
                i++;
                StringBuilder name = new StringBuilder();
                while (i < path.length()
                        && (Character.isLetterOrDigit(path.charAt(i)) || path.charAt(i) == '_')) {
                    name.append(path.charAt(i++));
                }
                paramNames.add(name.toString());
                regex.append("([^/]+)");
                continue;
            } else if (c == '*') {
                regex.append("(.*)");
            } else if (c == '(') {
                regex.append("(?:");
            } else if (".[\\^$|+".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
            i++;
        }
        if (prefix) {
            regex.append("(/.*)?");
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
