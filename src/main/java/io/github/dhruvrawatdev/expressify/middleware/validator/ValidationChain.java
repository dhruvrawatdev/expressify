package io.github.dhruvrawatdev.expressify.middleware.validator;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A fluent validation/sanitization chain for a single field (or a set of fields)
 * within one request location (body, query, params, headers, or cookies).
 *
 * <p>Implements {@link RouteHandler} so chains can be passed directly as middleware:
 *
 * <pre>{@code
 * app.post("/register",
 *     Validator.body("email").isEmail().notEmpty().withMessage("Valid email required"),
 *     Validator.body("age").isInt(18, 120).withMessage("Age must be 18–120"),
 *     Validator.query("page").isInt().optional(),
 *     (req, res, next) -> {
 *         ValidationResult errors = Validator.validationResult(req);
 *         if (!errors.isEmpty()) { res.status(422).json(errors.array()); return; }
 *         // ... handle request
 *     }
 * );
 * }</pre>
 */
public class ValidationChain implements RouteHandler {

    // Internal step model

    private enum StepKind { VALIDATOR, SANITIZER, BAIL }

    private static class Step {
        final StepKind kind;
        // validator
        BiFunction<Object, Request, Boolean> check;
        String message;
        // sanitizer
        Function<Object, Object> transform;

        static Step validator(BiFunction<Object, Request, Boolean> check, String message) {
            Step s = new Step(StepKind.VALIDATOR);
            s.check = check;
            s.message = message;
            return s;
        }

        static Step sanitizer(Function<Object, Object> transform) {
            Step s = new Step(StepKind.SANITIZER);
            s.transform = transform;
            return s;
        }

        static Step bail() { return new Step(StepKind.BAIL); }

        private Step(StepKind kind) { this.kind = kind; }
    }

    // Fields

    private final String   location;
    private final String[] fields;
    private final List<Step> steps = new ArrayList<>();
    private boolean optional = false;

    ValidationChain(String location, String[] fields) {
        this.location = location;
        this.fields   = fields;
    }

    // Modifiers

    /**
     * Override the error message for the most recently added validator.
     * Has no effect if the last step was a sanitizer or bail.
     */
    public ValidationChain withMessage(String message) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).kind == StepKind.VALIDATOR) {
                steps.get(i).message = message;
                break;
            }
        }
        return this;
    }

    /**
     * If the field is absent or empty, skip all subsequent validators (no error is added).
     * Sanitizers still run on the existing value.
     */
    public ValidationChain optional() {
        this.optional = true;
        return this;
    }

    /** Stop running validators for this field as soon as one fails. */
    public ValidationChain bail() {
        steps.add(Step.bail());
        return this;
    }

    // Validators

    /** Fail if the value is null, empty string, or blank. */
    public ValidationChain notEmpty() {
        steps.add(Step.validator(
                (v, req) -> v != null && !v.toString().isBlank(),
                "Value must not be empty"));
        return this;
    }

    /** Fail if the value is not a valid RFC 5322 email address (simplified check). */
    public ValidationChain isEmail() {
        Pattern p = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        steps.add(Step.validator(
                (v, req) -> v != null && p.matcher(v.toString()).matches(),
                "Invalid email address"));
        return this;
    }

    /** Fail if the string length is outside [min, max]. Pass -1 to skip a bound. */
    public ValidationChain isLength(int min, int max) {
        steps.add(Step.validator((v, req) -> {
            if (v == null) return false;
            int len = v.toString().length();
            if (min >= 0 && len < min) return false;
            if (max >= 0 && len > max) return false;
            return true;
        }, "Invalid length" + (min >= 0 ? " (min " + min + ")" : "") + (max >= 0 ? " (max " + max + ")" : "")));
        return this;
    }

    /** Fail if the value cannot be parsed as an integer. */
    public ValidationChain isInt() {
        steps.add(Step.validator((v, req) -> {
            if (v == null) return false;
            try { Long.parseLong(v.toString().trim()); return true; } catch (NumberFormatException e) { return false; }
        }, "Must be an integer"));
        return this;
    }

    /** Fail if the integer value is outside [min, max]. */
    public ValidationChain isInt(long min, long max) {
        steps.add(Step.validator((v, req) -> {
            if (v == null) return false;
            try {
                long n = Long.parseLong(v.toString().trim());
                return n >= min && n <= max;
            } catch (NumberFormatException e) { return false; }
        }, "Must be an integer between " + min + " and " + max));
        return this;
    }

    /** Fail if the value cannot be parsed as a floating-point number. */
    public ValidationChain isFloat() {
        steps.add(Step.validator((v, req) -> {
            if (v == null) return false;
            try { Double.parseDouble(v.toString().trim()); return true; } catch (NumberFormatException e) { return false; }
        }, "Must be a number"));
        return this;
    }

    /** Fail if the value contains any non-numeric characters. */
    public ValidationChain isNumeric() {
        steps.add(Step.validator(
                (v, req) -> v != null && v.toString().matches("-?\\d+(\\.\\d+)?"),
                "Must be numeric"));
        return this;
    }

    /** Fail if the value contains any non-alphabetic characters. */
    public ValidationChain isAlpha() {
        steps.add(Step.validator(
                (v, req) -> v != null && v.toString().matches("[a-zA-Z]+"),
                "Must contain only letters"));
        return this;
    }

    /** Fail if the value contains characters that are not letters or digits. */
    public ValidationChain isAlphanumeric() {
        steps.add(Step.validator(
                (v, req) -> v != null && v.toString().matches("[a-zA-Z0-9]+"),
                "Must contain only letters and numbers"));
        return this;
    }

    /** Fail if the value is not a string representation of a boolean ("true" or "false"). */
    public ValidationChain isBoolean() {
        steps.add(Step.validator(
                (v, req) -> v != null && (v.toString().equalsIgnoreCase("true") || v.toString().equalsIgnoreCase("false")),
                "Must be a boolean"));
        return this;
    }

    /** Fail if the value is not a well-formed URL (http or https). */
    public ValidationChain isURL() {
        Pattern p = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]*$", Pattern.CASE_INSENSITIVE);
        steps.add(Step.validator(
                (v, req) -> v != null && p.matcher(v.toString()).matches(),
                "Must be a valid URL"));
        return this;
    }

    /** Fail if the value is not a valid UUID (v1–v5). */
    public ValidationChain isUUID() {
        Pattern p = Pattern.compile(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
        steps.add(Step.validator(
                (v, req) -> v != null && p.matcher(v.toString()).matches(),
                "Must be a valid UUID"));
        return this;
    }

    /** Fail if the value does not match the given regular expression pattern. */
    public ValidationChain matches(String regex) {
        Pattern p = Pattern.compile(regex);
        steps.add(Step.validator(
                (v, req) -> v != null && p.matcher(v.toString()).matches(),
                "Value does not match the required pattern"));
        return this;
    }

    /** Fail if the value does not match the given compiled pattern. */
    public ValidationChain matches(Pattern pattern) {
        steps.add(Step.validator(
                (v, req) -> v != null && pattern.matcher(v.toString()).matches(),
                "Value does not match the required pattern"));
        return this;
    }

    /**
     * Run a custom validator function.
     * Return {@code true} (or a truthy value) to pass; throw or return {@code false} to fail.
     */
    public ValidationChain custom(BiFunction<Object, Request, Boolean> validator) {
        steps.add(Step.validator(validator, "Invalid value"));
        return this;
    }

    // Sanitizers

    /** Strip leading/trailing whitespace from the string value. */
    public ValidationChain trim() {
        steps.add(Step.sanitizer(v -> v == null ? null : v.toString().trim()));
        return this;
    }

    /** Convert the string value to lower-case. */
    public ValidationChain toLowerCase() {
        steps.add(Step.sanitizer(v -> v == null ? null : v.toString().toLowerCase()));
        return this;
    }

    /** Convert the string value to upper-case. */
    public ValidationChain toUpperCase() {
        steps.add(Step.sanitizer(v -> v == null ? null : v.toString().toUpperCase()));
        return this;
    }

    /** Parse the value as a long integer. Returns {@code null} if parsing fails. */
    public ValidationChain toInt() {
        steps.add(Step.sanitizer(v -> {
            if (v == null) return null;
            try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return null; }
        }));
        return this;
    }

    /** Parse the value as a double. Returns {@code null} if parsing fails. */
    public ValidationChain toFloat() {
        steps.add(Step.sanitizer(v -> {
            if (v == null) return null;
            try { return Double.parseDouble(v.toString().trim()); } catch (NumberFormatException e) { return null; }
        }));
        return this;
    }

    /** Convert "true"/"1"/"yes" (case-insensitive) to {@code true}, everything else to {@code false}. */
    public ValidationChain toBoolean() {
        steps.add(Step.sanitizer(v -> {
            if (v == null) return false;
            String s = v.toString().trim().toLowerCase();
            return s.equals("true") || s.equals("1") || s.equals("yes");
        }));
        return this;
    }

    /**
     * Apply a custom transformation to the field value.
     * The function receives the current value and must return the new value.
     */
    public ValidationChain customSanitizer(Function<Object, Object> fn) {
        steps.add(Step.sanitizer(fn));
        return this;
    }

    // RouteHandler

    @Override
    public void handle(Request req, Response res, NextFunction next) throws Exception {
        @SuppressWarnings("unchecked")
        List<ValidationError> accumulated =
                (List<ValidationError>) req.locals().computeIfAbsent(
                        Validator.ERRORS_KEY, k -> new ArrayList<>());

        for (String field : fields) {
            processField(field, req, accumulated);
        }

        next.run();
    }

    // Private

    private void processField(String field, Request req, List<ValidationError> accumulated) {
        Object[] valueHolder = {extractValue(req, field)};
        boolean absent = valueHolder[0] == null
                || (valueHolder[0] instanceof String s && s.isEmpty());

        if (optional && absent) return;

        boolean bailed = false;
        for (Step step : steps) {
            if (bailed) break;

            switch (step.kind) {
                case SANITIZER -> {
                    try {
                        valueHolder[0] = step.transform.apply(valueHolder[0]);
                    } catch (Exception ignored) {}
                }
                case VALIDATOR -> {
                    boolean valid;
                    try {
                        valid = Boolean.TRUE.equals(step.check.apply(valueHolder[0], req));
                    } catch (Exception e) {
                        valid = false;
                    }
                    if (!valid) {
                        accumulated.add(new ValidationError(location, field, valueHolder[0], step.message));
                    }
                }
                case BAIL -> {
                    // bail if any error already recorded for this field
                    boolean hasFieldError = accumulated.stream()
                            .anyMatch(e -> e.field().equals(field) && e.location().equals(location));
                    if (hasFieldError) bailed = true;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Object extractValue(Request req, String field) {
        return switch (location) {
            case "query" -> req.query(field);
            case "params" -> req.param(field);
            case "headers" -> req.get(field);
            case "cookies" -> req.cookies().get(field);
            case "body" -> req.body(field);
            default -> null;
        };
    }

    /** Navigate dot-separated path into a nested map. */
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
