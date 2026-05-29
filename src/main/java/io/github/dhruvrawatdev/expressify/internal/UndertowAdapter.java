package io.github.dhruvrawatdev.expressify.internal;

import io.github.dhruvrawatdev.expressify.router.middleware.MiddlewareChain;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.ParamHandler;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.router.core.RouteEntry;
import io.github.dhruvrawatdev.expressify.router.registry.RouteRegistry;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Undertow HttpHandler that bridges to the Expressify routing engine.
 * Dispatches to a worker thread, then walks the middleware + route chain.
 */
public class UndertowAdapter implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(UndertowAdapter.class);

    private final RouteRegistry              registry;
    private final Map<String, Object>        settings;
    private final Map<String, TemplateEngine> engines;

    private final ThreadLocal<Request>  reqPool = new ThreadLocal<>();
    private final ThreadLocal<Response> resPool = new ThreadLocal<>();

    public UndertowAdapter(RouteRegistry registry,
                           Map<String, Object> settings,
                           Map<String, TemplateEngine> engines) {
        this.registry = registry;
        this.settings = settings;
        this.engines  = engines;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        exchange.startBlocking();

        Request reqObj = reqPool.get();
        if (reqObj == null) { reqObj = new Request(exchange); reqPool.set(reqObj); }
        else reqObj.reset(exchange);
        final Request req = reqObj;

        Response resObj = resPool.get();
        if (resObj == null) { resObj = new Response(exchange, settings, engines); resPool.set(resObj); }
        else resObj.reset(exchange, settings, engines);
        final Response res = resObj;

        req.setSettings(settings);
        req.setResponse(res);
        res.setRequest(req);

        if (Boolean.TRUE.equals(settings.get("x-powered-by"))) {
            exchange.getResponseHeaders().put(HttpHeaders.X_POWERED_BY,
                    HttpHeaders.VAL_X_POWERED_BY);
        }

        // Normalise the routing path: collapse repeated slashes, strip trailing slash
        // (except root "/"), so /api//users and /api/users/ both match /api/users.
        String rawPath   = req.getRoutingPath();
        String normalised = rawPath.replaceAll("/+", "/");
        if (normalised.length() > 1 && normalised.endsWith("/")) {
            normalised = normalised.substring(0, normalised.length() - 1);
        }
        if (!normalised.equals(rawPath)) req.setRoutingPath(normalised);

        NextFunction rootFallthrough = new NextFunction() {
            @Override public void run() throws Exception {
                if (!res.isCommitted()) {
                    res.status(404).send("Cannot " + req.method() + " " + req.path());
                }
            }
            @Override public void error(Throwable err) throws Exception {
                sendDefaultError(res, err);
            }
        };

        try {
            runEntries(registry.getEntries(), req, res, 0, null, rootFallthrough,
                    registry.getParamHandlers());
        } catch (Throwable e) {
            log.error("Unhandled error escaped dispatch chain — sending 500", e);
            sendDefaultError(res, e);
        }
    }

    // Chain execution

    private void runEntries(List<RouteEntry> entries, Request req, Response res,
                            int index, Throwable err, NextFunction fallthrough,
                            Map<String, List<ParamHandler>> paramHandlers) throws Exception {

        if (index >= entries.size()) {
            if (err != null) fallthrough.error(err);
            else fallthrough.run();
            return;
        }

        RouteEntry entry = entries.get(index);

        // Error handler
        if (entry.type == RouteEntry.Type.ERROR_HANDLER) {
            if (err != null) {
                NextFunction next = buildNext(entries, req, res, index, fallthrough, paramHandlers);
                try {
                    entry.errorHandler.handle(err, req, res, next);
                } catch (Throwable e) {
                    log.error("Error handler threw — propagating", e);
                    runEntries(entries, req, res, index + 1, e, fallthrough, paramHandlers);
                }
            } else {
                runEntries(entries, req, res, index + 1, null, fallthrough, paramHandlers);
            }
            return;
        }

        // Skip normal entries while an error is propagating
        if (err != null) {
            runEntries(entries, req, res, index + 1, err, fallthrough, paramHandlers);
            return;
        }

        // Path matching
        if (!matchesRequest(entry, req)) {
            runEntries(entries, req, res, index + 1, null, fallthrough, paramHandlers);
            return;
        }

        // Router mount
        if (entry.type == RouteEntry.Type.ROUTER) {
            String currentPath = req.getRoutingPath();
            String currentBase = req.baseUrl();
            String prefix = entry.pathPrefix;
            String subPath;

            if (prefix == null || prefix.isBlank() || prefix.equals("/")) {
                subPath = currentPath;
            } else {
                subPath = currentPath.substring(prefix.length());
                if (subPath.isEmpty()) subPath = "/";
            }

            req.setRoutingPath(subPath);
            req.setBaseUrl(currentBase + (prefix != null ? prefix : ""));

            NextFunction subFallthrough = new NextFunction() {
                @Override public void run() throws Exception {
                    req.setRoutingPath(currentPath);
                    req.setBaseUrl(currentBase);
                    runEntries(entries, req, res, index + 1, null, fallthrough, paramHandlers);
                }
                @Override public void error(Throwable subErr) throws Exception {
                    req.setRoutingPath(currentPath);
                    req.setBaseUrl(currentBase);
                    runEntries(entries, req, res, index + 1, subErr, fallthrough, paramHandlers);
                }
            };

            runEntries(entry.mountedRouter.getEntries(), req, res, 0, null, subFallthrough,
                    entry.mountedRouter.getParamHandlers());
            // Restore in case sub-router exited without calling fallthrough
            req.setRoutingPath(currentPath);
            req.setBaseUrl(currentBase);
            return;
        }

        // Path-prefixed middleware: strip prefix before running handlers
        if (entry.type == RouteEntry.Type.MIDDLEWARE
                && entry.pathPrefix != null
                && !entry.pathPrefix.isBlank()
                && !entry.pathPrefix.equals("/")) {

            String savedPath = req.getRoutingPath();
            String stripped  = savedPath.substring(entry.pathPrefix.length());
            if (stripped.isEmpty()) stripped = "/";
            req.setRoutingPath(stripped);

            final String pathToRestore = savedPath;
            NextFunction wrappedNext = new NextFunction() {
                @Override public void run() throws Exception {
                    req.setRoutingPath(pathToRestore);
                    runEntries(entries, req, res, index + 1, null, fallthrough, paramHandlers);
                }
                @Override public void error(Throwable e) throws Exception {
                    req.setRoutingPath(pathToRestore);
                    runEntries(entries, req, res, index + 1, e, fallthrough, paramHandlers);
                }
            };

            new MiddlewareChain(entry.handlers, req, res, wrappedNext).execute();
            req.setRoutingPath(savedPath);
            return;
        }

        // Route or global middleware: extract params, run handlers
        if (!entry.paramNames.isEmpty() && entry.pathPattern != null) {
            Map<String, String> params = new HashMap<>();
            if (entry.pathPattern.match(req.getRoutingPath(), params)) {
                req.setParams(params);
            }
        }

        NextFunction next = buildNext(entries, req, res, index, fallthrough, paramHandlers);
        List<RouteHandler> handlers = buildHandlerChain(entry, req, paramHandlers);
        new MiddlewareChain(handlers, req, res, next).execute();
    }

    // Helpers

    private boolean matchesRequest(RouteEntry entry, Request req) {
        if (entry.method != null && !"ALL".equals(entry.method)) {
            String reqMethod = req.method();
            if (!entry.method.equalsIgnoreCase(reqMethod)) {
                if (!("HEAD".equalsIgnoreCase(reqMethod) && "GET".equalsIgnoreCase(entry.method))) {
                    return false;
                }
            }
        }
        if (entry.pathPattern == null) return true;
        return entry.pathPattern.matches(req.getRoutingPath());
    }

    private NextFunction buildNext(List<RouteEntry> entries, Request req, Response res,
                                   int index, NextFunction fallthrough,
                                   Map<String, List<ParamHandler>> paramHandlers) {
        return new NextFunction() {
            @Override public void run() throws Exception {
                runEntries(entries, req, res, index + 1, null, fallthrough, paramHandlers);
            }
            @Override public void error(Throwable e) throws Exception {
                runEntries(entries, req, res, index + 1, e, fallthrough, paramHandlers);
            }
        };
    }

    private List<RouteHandler> buildHandlerChain(RouteEntry entry, Request req,
                                                  Map<String, List<ParamHandler>> paramHandlers) {
        if (paramHandlers == null || paramHandlers.isEmpty() || entry.paramNames.isEmpty()) {
            return entry.handlers;
        }
        Map<String, String> currentParams = req.params();
        List<RouteHandler> combined = new ArrayList<>();
        for (String paramName : entry.paramNames) {
            String paramValue = currentParams.get(paramName);
            if (paramValue == null) continue;
            List<ParamHandler> pHandlers = paramHandlers.get(paramName);
            if (pHandlers != null) {
                for (ParamHandler ph : pHandlers) {
                    final String v = paramValue;
                    combined.add((rq, rs, nx) -> ph.handle(rq, rs, nx, v));
                }
            }
        }
        if (combined.isEmpty()) return entry.handlers;
        combined.addAll(entry.handlers);
        return combined;
    }

    private static void sendDefaultError(Response res, Throwable err) {
        if (res.isCommitted()) return;
        try {
            String message = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
            res.status(500).json(java.util.Map.of(
                    "error",   "Internal Server Error",
                    "message", message,
                    "type",    err.getClass().getSimpleName()
            ));
        } catch (Throwable t) {
            try { res.status(500).end(); } catch (Throwable ignored) {}
        }
    }
}
