package io.github.dhruvrawatdev.expressify.middleware.dev_error_handler;

import io.github.dhruvrawatdev.expressify.router.handler.ErrorHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Development error handler — port of the Node.js {@code errorhandler} npm package.
 *
 * <p>Renders detailed error information (message, stack trace, HTTP status) in the
 * response format the client prefers (HTML, JSON, or plain text).
 * <strong>For development only — do not use in production.</strong>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Register LAST, after all routes and middleware
 * if (app.enabled("development")) {
 *     app.error(DevErrorHandler.create());
 * } else {
 *     app.error((err, req, res, next) ->
 *         res.status(500).json(Map.of("error", "Internal Server Error")));
 * }
 * }</pre>
 *
 * <h2>Content negotiation</h2>
 * <p>The response format is determined by the request's {@code Accept} header:
 * <ul>
 *   <li>{@code text/html} — formatted HTML page with stack trace</li>
 *   <li>{@code application/json} — JSON object with {@code status}, {@code message}, and {@code stack}</li>
 *   <li>{@code text/plain} or anything else — plain-text error and stack</li>
 * </ul>
 *
 * <h2>HTTP status</h2>
 * <p>The response status is taken from the exception in this order:
 * <ol>
 *   <li>A {@code getStatus()} method on the exception (if it exists and returns 400–599)</li>
 *   <li>A {@code getStatusCode()} method on the exception (same rule)</li>
 *   <li>Default: {@code 500 Internal Server Error}</li>
 * </ol>
 *
 * <h2>Suppressing logging</h2>
 * <pre>{@code
 * app.error(DevErrorHandler.create(DevErrorHandlerOptions.builder()
 *     .log(false)
 *     .build()));
 * }</pre>
 */
public final class DevErrorHandler {

    private DevErrorHandler() {}

    /**
     * Create a development error handler with default options (logging enabled).
     *
     * <p>Register this as the last handler via {@code app.error(DevErrorHandler.create())}.
     *
     * @return an {@link ErrorHandler} that renders detailed error information
     */
    public static ErrorHandler create() {
        return create(DevErrorHandlerOptions.defaults());
    }

    /**
     * Create a development error handler with custom options.
     *
     * <pre>{@code
     * app.error(DevErrorHandler.create(DevErrorHandlerOptions.builder()
     *     .log(false)  // suppress System.err output
     *     .build()));
     * }</pre>
     *
     * @param opts handler options; build with {@link DevErrorHandlerOptions#builder()}
     * @return an {@link ErrorHandler} that renders detailed error information
     */
    public static ErrorHandler create(DevErrorHandlerOptions opts) {
        DevErrorHandlerOptions o = opts != null ? opts : DevErrorHandlerOptions.defaults();
        return (err, req, res, next) -> {
            if (o.isLog()) {
                System.err.println("[DevErrorHandler] " + err);
                err.printStackTrace(System.err);
            }

            int status = resolveStatus(err);
            String stackTrace = getStackTrace(err);
            String message = err.getMessage() != null ? err.getMessage() : err.getClass().getName();

            res.status(status);
            res.set("X-Content-Type-Options", "nosniff");

            String accept = req.get("Accept");
            if (accept != null && accept.contains("text/html")) {
                res.set("Content-Type", "text/html; charset=utf-8")
                   .send(renderHtml(err, status, message, stackTrace));
            } else if (accept != null && accept.contains("application/json")) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status",  status);
                body.put("message", message);
                body.put("stack",   stackTrace);
                res.json(body);
            } else {
                res.set("Content-Type", "text/plain; charset=utf-8")
                   .send(status + " " + statusText(status) + "\n\n" + message + "\n\n" + stackTrace);
            }
        };
    }

    // Internal

    private static int resolveStatus(Throwable err) {
        // Check getStatus() via reflection (mirrors err.status in Express.js)
        try {
            java.lang.reflect.Method m = err.getClass().getMethod("getStatus");
            Object v = m.invoke(err);
            if (v instanceof Number n) {
                int s = n.intValue();
                if (s >= 400 && s < 600) return s;
            }
        } catch (Exception ignored) {}
        // Check getStatusCode() (common in HTTP client libraries)
        try {
            java.lang.reflect.Method m = err.getClass().getMethod("getStatusCode");
            Object v = m.invoke(err);
            if (v instanceof Number n) {
                int s = n.intValue();
                if (s >= 400 && s < 600) return s;
            }
        } catch (Exception ignored) {}
        return 500;
    }

    private static String getStackTrace(Throwable err) {
        StringWriter sw = new StringWriter(512);
        err.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String statusText(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 409 -> "Conflict";
            case 410 -> "Gone";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default  -> "Error";
        };
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String renderHtml(Throwable err, int status, String message, String stack) {
        String errorType = escapeHtml(err.getClass().getName());
        String safeMsg = escapeHtml(message);
        String safeStack = escapeHtml(stack);
        String statusStr = status + " " + statusText(status);

        return "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\">\n"
            + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "  <title>Error: " + status + "</title>\n"
            + "  <style>\n"
            + "    *{box-sizing:border-box;margin:0;padding:0}\n"
            + "    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            + "background:#f5f5f5;color:#333;padding:2em}\n"
            + "    .card{background:#fff;border-top:4px solid #e74c3c;border-radius:6px;"
            + "max-width:900px;margin:0 auto;padding:2em;box-shadow:0 2px 8px rgba(0,0,0,.1)}\n"
            + "    h1{font-size:1.4em;color:#e74c3c;margin-bottom:.4em}\n"
            + "    .status{font-size:.9em;color:#888;margin-bottom:1.2em}\n"
            + "    .label{font-size:.8em;font-weight:600;color:#666;text-transform:uppercase;"
            + "letter-spacing:.05em;margin:1em 0 .4em}\n"
            + "    .message{background:#fef9f9;border:1px solid #fcc;border-radius:4px;"
            + "padding:.8em 1em;font-family:monospace;font-size:.9em;word-break:break-word}\n"
            + "    .stack{background:#f8f8f8;border:1px solid #ddd;border-radius:4px;"
            + "padding:.8em 1em;font-family:monospace;font-size:.8em;white-space:pre-wrap;"
            + "word-break:break-word;max-height:400px;overflow:auto}\n"
            + "    .hint{margin-top:1.5em;font-size:.8em;color:#aaa;border-top:1px solid #eee;padding-top:1em}\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"
            + "  <div class=\"card\">\n"
            + "    <h1>" + errorType + "</h1>\n"
            + "    <div class=\"status\">HTTP " + escapeHtml(statusStr) + "</div>\n"
            + "    <div class=\"label\">Message</div>\n"
            + "    <div class=\"message\">" + safeMsg + "</div>\n"
            + "    <div class=\"label\">Stack Trace</div>\n"
            + "    <div class=\"stack\">" + safeStack + "</div>\n"
            + "    <div class=\"hint\">This response is only shown in development mode.</div>\n"
            + "  </div>\n"
            + "</body>\n"
            + "</html>";
    }
}
