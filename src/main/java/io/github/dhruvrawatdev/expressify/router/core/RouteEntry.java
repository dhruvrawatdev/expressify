package io.github.dhruvrawatdev.expressify.router.core;

import io.github.dhruvrawatdev.expressify.router.handler.ErrorHandler;
import io.github.dhruvrawatdev.expressify.router.handler.ParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import io.github.dhruvrawatdev.expressify.router.internal.PathPattern;

import java.util.List;
import java.util.Map;

/**
 * Internal representation of a single routing entry (middleware, route, or router mount).
 */
public class RouteEntry {

    public enum Type {
        /** Global or path-prefix middleware. Uses prefix (startsWith) matching. */
        MIDDLEWARE,
        /** Exact-path route (GET /path, POST /path, etc.). */
        ROUTE,
        /** A mounted Router instance. */
        ROUTER,
        /** The global error handler (4-param form). */
        ERROR_HANDLER
    }

    public final Type type;
    public final String method;        // null = any HTTP method
    public final PathPattern pathPattern;   // compiled path pattern
    public final List<String> paramNames;    // named params in order (e.g. [:id, :name])
    public final String pathPrefix;    // original path string (for MIDDLEWARE/ROUTER)
    public final List<RouteHandler> handlers;
    public final RouterRef mountedRouter; // non-null for ROUTER type
    public final ErrorHandler errorHandler;  // non-null for ERROR_HANDLER type

    private RouteEntry(Builder b) {
        this.type = b.type;
        this.method = b.method;
        this.pathPattern = b.pathPattern;
        this.paramNames = b.paramNames;
        this.pathPrefix = b.pathPrefix;
        this.handlers = b.handlers;
        this.mountedRouter = b.mountedRouter;
        this.errorHandler = b.errorHandler;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Type type;
        private String method;
        private PathPattern pathPattern;
        private List<String> paramNames  = List.of();
        private String pathPrefix  = "";
        private List<RouteHandler> handlers    = List.of();
        private RouterRef mountedRouter;
        private ErrorHandler errorHandler;

        public Builder type(Type v) { this.type = v; return this; }
        public Builder method(String v) { this.method = v; return this; }
        public Builder pathPattern(PathPattern v) { this.pathPattern = v; return this; }
        public Builder paramNames(List<String> v) { this.paramNames = v; return this; }
        public Builder pathPrefix(String v) { this.pathPrefix = v; return this; }
        public Builder handlers(List<RouteHandler> v) { this.handlers = v; return this; }
        public Builder mountedRouter(RouterRef v) { this.mountedRouter = v; return this; }
        public Builder errorHandler(ErrorHandler v) { this.errorHandler = v; return this; }

        public RouteEntry build() { return new RouteEntry(this); }
    }

    /**
     * Thin reference to a Router to avoid a direct circular dependency with Router.java.
     * Router implements this interface.
     */
    public interface RouterRef {
        List<RouteEntry> getEntries();
        Map<String, List<ParamHandler>> getParamHandlers();
    }
}
