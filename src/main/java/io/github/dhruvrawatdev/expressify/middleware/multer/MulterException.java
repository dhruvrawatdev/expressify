package io.github.dhruvrawatdev.expressify.middleware.multer;

/**
 * Exception thrown by {@link Multer} on upload validation failures.
 * Mirrors the {@code MulterError} class from the multer npm package.
 */
public class MulterException extends RuntimeException {

    public static final String LIMIT_FILE_SIZE = "LIMIT_FILE_SIZE";
    public static final String LIMIT_FILE_COUNT = "LIMIT_FILE_COUNT";
    public static final String LIMIT_FIELD_KEY = "LIMIT_FIELD_KEY";
    public static final String LIMIT_FIELD_VALUE = "LIMIT_FIELD_VALUE";
    public static final String LIMIT_FIELD_COUNT = "LIMIT_FIELD_COUNT";
    public static final String LIMIT_UNEXPECTED_FILE = "LIMIT_UNEXPECTED_FILE";
    public static final String LIMIT_PART_COUNT = "LIMIT_PART_COUNT";

    private final String code;
    private String field;

    public MulterException(String code) {
        super(code);
        this.code = code;
    }

    public MulterException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode()  { return code; }
    public String getField() { return field; }
    public MulterException field(String f) { this.field = f; return this; }

    @Override
    public String toString() {
        return "MulterError [" + code + "]: " + getMessage();
    }
}
