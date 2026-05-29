package io.github.dhruvrawatdev.expressify.exceptions;

public class ForbiddenException extends HttpException {
    public ForbiddenException(String message) { super(403, message); }
    public ForbiddenException() { super(403, "Forbidden"); }
}
