package io.github.dhruvrawatdev.expressify.middleware.validator;

/**
 * A single field validation failure — mirrors express-validator's FieldValidationError.
 */
public class ValidationError {

    private final String location;
    private final String field;
    private final Object value;
    private final String message;

    ValidationError(String location, String field, Object value, String message) {
        this.location = location;
        this.field = field;
        this.value = value;
        this.message = message;
    }

    /** Where the field came from: "body", "query", "params", "headers", or "cookies". */
    public String location() { return location; }

    /** The field name (or dot-path for nested fields). */
    public String field() { return field; }

    /** The raw value that failed validation (may be null if field was absent). */
    public Object value() { return value; }

    /** The validation error message. */
    public String message() { return message; }

    /** Alias for {@link #message()} — Express-compatible getter name. */
    public String msg() { return message; }

    @Override
    public String toString() {
        return "ValidationError{location='" + location + "', field='" + field
                + "', value=" + value + ", message='" + message + "'}";
    }
}
