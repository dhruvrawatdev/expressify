package io.github.dhruvrawatdev.expressify.middleware.morgan;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HTTP request logger middleware — port of the Node.js {@code morgan} package.
 *
 * <p>Built-in formats:
 * <ul>
 * <li>{@code dev}  — Coloured, concise output for development</li>
 * <li>{@code combined} — Apache combined log format</li>
 * <li>{@code common} — Apache common log format</li>
 * <li>{@code short}  — Shorter than default, includes response time</li>
 * <li>{@code tiny}   — Minimal output</li>
 * </ul>
 *
 * <pre>{@code
 * app.use(Morgan.dev());
 * app.use(Morgan.create("combined"));
 * app.use(Morgan.create("tiny", MorganOptions.builder().skip((req, res) -> res.getStatus() < 400).build()));
 * }</pre>
 */
public class Morgan {

  // ANSI escape codes
  private static final String RESET = "[0m";
  private static final String RED = "[31m";
  private static final String YELLOW = "[33m";
  private static final String CYAN = "[36m";
  private static final String GREEN = "[32m";

  private Morgan() {}

  /**
   * Logger with {@code "dev"} format — ANSI-coloured status code, method, URL, and elapsed time.
   * Ideal for development: {@code GET /users 200 3.142 ms - 128}.
   *
   * @return a {@link RouteHandler} that logs each request to {@code stdout} after the response
   */
  public static RouteHandler dev() { return create("dev"); }

  /**
   * Logger with Apache {@code "combined"} format — includes remote IP, date, referrer, user-agent.
   * Suitable for production log files.
   *
   * @return a {@link RouteHandler} that logs Apache combined-format lines to {@code stdout}
   */
  public static RouteHandler combined() { return create("combined"); }

  /**
   * Logger with Apache {@code "common"} format — IP, date, method, status, content length.
   *
   * @return a {@link RouteHandler} that logs Apache common-format lines to {@code stdout}
   */
  public static RouteHandler common() { return create("common"); }

  /**
   * Logger with {@code "short"} format — IP, method, URL, status, length, and response time.
   *
   * @return a {@link RouteHandler} that logs short-format lines to {@code stdout}
   */
  public static RouteHandler short_() { return create("short"); }

  /**
   * Logger with {@code "tiny"} format — the most minimal one-liner: method, URL, status, length, time.
   *
   * @return a {@link RouteHandler} that logs tiny-format lines to {@code stdout}
   */
  public static RouteHandler tiny() { return create("tiny"); }

  /**
   * Create a logger with the given format name and default options (logs to {@code stdout}).
   *
   * @param format one of {@code "dev"}, {@code "combined"}, {@code "common"},
   *     {@code "short"}, {@code "tiny"}; any other value falls back to {@code "dev"}
   * @return a {@link RouteHandler} that logs each completed request
   */
  public static RouteHandler create(String format) {
    return create(format, MorganOptions.defaults());
  }

  /**
   * Create a logger with the given format and custom options.
   *
   * <pre>{@code
   * // Log only errors (4xx/5xx) to stderr
   * app.use(Morgan.create("combined", MorganOptions.builder()
   *   .stream(System.err)
   *   .skip((req, res) -> res.getStatus() < 400)
   *   .build()));
   * }</pre>
   *
   * @param format  one of {@code "dev"}, {@code "combined"}, {@code "common"},
   *      {@code "short"}, {@code "tiny"}
   * @param options output stream, skip predicate, and immediate-mode flag;
   *      build with {@link MorganOptions#builder()}
   * @return a {@link RouteHandler} that logs each request according to the given format and options
   */
  public static RouteHandler create(String format, MorganOptions options) {
    return (req, res, next) -> {
    if (options.isImmediate()) {
      log(format, options, req, res, 0);
      next.run();
      return;
    }
    long startNs = System.nanoTime();
    try {
      next.run();
    } finally {
      double elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0;
      if (options.getSkip() == null || !options.getSkip().test(req, res)) {
        log(format, options, req, res, elapsedMs);
      }
    }
    };
  }

  // Private logging

  private static void log(String format, MorganOptions opts,
          Request req, Response res, double ms) {
    String line = switch (format.toLowerCase()) {
    case "combined" -> combined(req, res, ms);
    case "common" -> common(req, res, ms);
    case "short"  -> short_(req, res, ms);
    case "tiny"   -> tiny(req, res, ms);
    default   -> dev(req, res, ms);
    };
    if (line != null) opts.getStream().println(line);
  }

  private static String dev(Request req, Response res, double ms) {
    int  status = res.getStatus();
    String color  = status >= 500 ? RED
        : status >= 400 ? YELLOW
        : status >= 300 ? CYAN
        : GREEN;
    return String.format("%s %s %s%d%s %.3f ms - %s",
      req.method(), req.originalUrl(),
      color, status, RESET,
      ms, contentLength(res));
  }

  private static String combined(Request req, Response res, double ms) {
    return String.format("%s - - [%s] \"%s %s HTTP/1.1\" %d %s \"%s\" \"%s\"",
      req.ip(), clfDate(),
      req.method(), req.originalUrl(),
      res.getStatus(), contentLength(res),
      dash(req.get("Referer")),
      dash(req.get("User-Agent")));
  }

  private static String common(Request req, Response res, double ms) {
    return String.format("%s - - [%s] \"%s %s HTTP/1.1\" %d %s",
      req.ip(), clfDate(),
      req.method(), req.originalUrl(),
      res.getStatus(), contentLength(res));
  }

  private static String short_(Request req, Response res, double ms) {
    return String.format("%s - %s %s HTTP/1.1 %d %s - %.3f ms",
      req.ip(),
      req.method(), req.originalUrl(),
      res.getStatus(), contentLength(res), ms);
  }

  private static String tiny(Request req, Response res, double ms) {
    return String.format("%s %s %d %s - %.3f ms",
      req.method(), req.originalUrl(),
      res.getStatus(), contentLength(res), ms);
  }

  private static String contentLength(Response res) {
    String cl = res.getHeader("Content-Length");
    return cl != null ? cl : "-";
  }

  private static String dash(String value) {
    return (value != null && !value.isEmpty()) ? value : "-";
  }

  private static final DateTimeFormatter CLF_FORMATTER =
    DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

  private static String clfDate() {
    return ZonedDateTime.now(ZoneOffset.UTC).format(CLF_FORMATTER);
  }
}
