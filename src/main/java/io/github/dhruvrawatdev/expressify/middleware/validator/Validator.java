package io.github.dhruvrawatdev.expressify.middleware.validator;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.util.*;

/**
 * Entry point for the express-validator-style request validation API.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * import static io.github.dhruvrawatdev.expressify.middleware.validator.Validator.*;
 *
 * app.post("/register",
 *     body("email").isEmail().notEmpty().withMessage("Valid email required"),
 *     body("password").isLength(8, 100).withMessage("Password too short"),
 *     body("age").isInt(0, 150).optional(),
 *     query("ref").isAlphanumeric().optional(),
 *     (req, res, next) -> {
 *         ValidationResult errors = validationResult(req);
 *         if (!errors.isEmpty()) {
 *             res.status(422).json(errors.array());
 *             return;
 *         }
 *         // safe to use validated values
 *     }
 * );
 * }</pre>
 *
 * <h2>Available validators</h2>
 * {@code notEmpty}, {@code isEmail}, {@code isLength(min, max)}, {@code isInt()},
 * {@code isInt(min, max)}, {@code isFloat}, {@code isNumeric}, {@code isAlpha},
 * {@code isAlphanumeric}, {@code isBoolean}, {@code isURL}, {@code isUUID},
 * {@code matches(regex)}, {@code custom(fn)}
 *
 * <h2>Available sanitizers</h2>
 * {@code trim}, {@code toLowerCase}, {@code toUpperCase}, {@code toInt},
 * {@code toFloat}, {@code toBoolean}, {@code customSanitizer(fn)}
 */
public final class Validator {

    /** Request-locals key under which accumulated errors are stored. */
    static final String ERRORS_KEY = "__validationErrors";

    private Validator() {}

    // ── Field selectors ────────────────────────────────────────────────────

    /**
     * Validate one or more fields from the parsed request body (JSON object or URL-encoded form).
     *
     * <p>Supports dot-notation for nested fields: {@code body("address.city")}.
     * The returned chain is itself a {@link io.github.dhruvrawatdev.expressify.router.handler.RouteHandler}
     * — pass it directly to a route registration method.
     *
     * <pre>{@code
     * app.post("/register",
     *     body("email").isEmail().normalizeEmail(),
     *     body("password").isLength(8, 100).withMessage("Min 8 characters"),
     *     (req, res, next) -> {
     *         ValidationResult errors = validationResult(req);
     *         if (!errors.isEmpty()) { res.status(422).json(errors.array()); return; }
     *         next.run();
     *     }
     * );
     * }</pre>
     *
     * @param fields one or more body field names; dot-notation supported for nested paths
     * @return a {@link ValidationChain} that acts as middleware and accumulates errors on the request
     */
    public static ValidationChain body(String... fields) {
        return new ValidationChain("body", fields);
    }

    /**
     * Validate one or more query-string parameters (e.g., {@code ?page=1&limit=10}).
     *
     * @param fields one or more query parameter names to validate
     * @return a {@link ValidationChain} middleware that validates and accumulates errors
     */
    public static ValidationChain query(String... fields) {
        return new ValidationChain("query", fields);
    }

    /**
     * Validate one or more route path parameters (e.g., {@code :id} in {@code /users/:id}).
     *
     * @param fields one or more route parameter names to validate
     * @return a {@link ValidationChain} middleware that validates and accumulates errors
     */
    public static ValidationChain param(String... fields) {
        return new ValidationChain("params", fields);
    }

    /**
     * Validate one or more request headers (case-insensitive).
     *
     * @param fields one or more header names to validate (e.g., {@code "Authorization"})
     * @return a {@link ValidationChain} middleware that validates and accumulates errors
     */
    public static ValidationChain header(String... fields) {
        return new ValidationChain("headers", fields);
    }

    /**
     * Validate one or more cookie values (requires {@link io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieParser} upstream).
     *
     * @param fields one or more cookie names to validate
     * @return a {@link ValidationChain} middleware that validates and accumulates errors
     */
    public static ValidationChain cookie(String... fields) {
        return new ValidationChain("cookies", fields);
    }

    // Result accessors

    /**
     * Collect all errors produced by every {@link ValidationChain} that ran on this request.
     *
     * <pre>{@code
     * ValidationResult result = Validator.validationResult(req);
     * if (!result.isEmpty()) {
     *     res.status(422).json(result.array());
     *     return;
     * }
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static ValidationResult validationResult(Request req) {
        Object raw = req.locals().get(ERRORS_KEY);
        if (raw instanceof List<?> list) {
            return new ValidationResult((List<ValidationError>) list);
        }
        return new ValidationResult(Collections.emptyList());
    }

    /**
     * Returns only the request fields that passed through at least one validation chain,
     * keyed by {@code location.field}. Useful for whitelisting input.
     *
     * <pre>{@code
     * Map<String, Object> data = Validator.matchedData(req);
     * String email = (String) data.get("email");
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> matchedData(Request req) {
        Object raw = req.locals().get(ERRORS_KEY);
        if (!(raw instanceof List<?> list)) return Collections.emptyMap();

        // Build set of (location, field) pairs that were touched
        // We track these in a separate key set during chain execution
        Object touched = req.locals().get("__validationTouched");
        if (!(touched instanceof Set<?> set)) return Collections.emptyMap();

        Map<String, Object> result = new LinkedHashMap<>();
        for (Object item : set) {
            if (item instanceof String[] pair && pair.length == 2) {
                String loc = pair[0];
                String field = pair[1];
                Object val = extractFrom(req, loc, field);
                if (val != null) result.put(field, val);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    // Private helpers

    @SuppressWarnings("unchecked")
    private static Object extractFrom(Request req, String location, String field) {
        return switch (location) {
            case "query" -> req.query(field);
            case "params" -> req.param(field);
            case "headers" -> req.get(field);
            case "cookies" -> req.cookies().get(field);
            case "body" -> req.body(field);
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static Object deepGet(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) current = m.get(part);
            else return null;
        }
        return current;
    }
}
