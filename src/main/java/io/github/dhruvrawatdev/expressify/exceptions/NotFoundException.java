package io.github.dhruvrawatdev.expressify.exceptions;

public class NotFoundException extends HttpException {
    public NotFoundException(String message) { super(404, message); }
    public NotFoundException() { super(404, "Not Found"); }
}
