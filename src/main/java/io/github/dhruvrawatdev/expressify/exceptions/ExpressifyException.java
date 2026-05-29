package io.github.dhruvrawatdev.expressify.exceptions;

public class ExpressifyException extends RuntimeException {

    public ExpressifyException(String message) {
        super(message);
    }

    public ExpressifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
