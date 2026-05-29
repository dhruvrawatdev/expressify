package io.github.dhruvrawatdev.expressify.middleware.validator;

import java.util.*;

/**
 * The aggregated result of all {@link ValidationChain} middlewares on a request.
 * Retrieve via {@link Validator#validationResult(io.github.dhruvrawatdev.expressify.http.Request)}.
 *
 * <pre>{@code
 * ValidationResult result = Validator.validationResult(req);
 * if (!result.isEmpty()) {
 *     res.status(422).json(result.array());
 *     return;
 * }
 * }</pre>
 */
public class ValidationResult {

    private final List<ValidationError> errors;

    ValidationResult(List<ValidationError> errors) {
        this.errors = Collections.unmodifiableList(errors);
    }

    /** Returns {@code true} when there are no validation errors. */
    public boolean isEmpty() { return errors.isEmpty(); }

    /** All validation errors as an unmodifiable list. */
    public List<ValidationError> array() { return errors; }

    /** The first validation error, or {@code null} if there are none. */
    public ValidationError first() { return errors.isEmpty() ? null : errors.get(0); }

    /**
     * Errors keyed by field name. When a field has multiple errors, the first is kept.
     * Useful for mapping errors onto a form.
     */
    public Map<String, ValidationError> mapped() {
        Map<String, ValidationError> map = new LinkedHashMap<>();
        for (ValidationError e : errors) {
            map.putIfAbsent(e.field(), e);
        }
        return Collections.unmodifiableMap(map);
    }

    /** Total number of validation errors. */
    public int size() { return errors.size(); }

    @Override
    public String toString() { return "ValidationResult{errors=" + errors + '}'; }
}
