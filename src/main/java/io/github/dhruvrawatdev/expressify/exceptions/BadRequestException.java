package io.github.dhruvrawatdev.expressify.exceptions;

public class BadRequestException extends HttpException {
    public BadRequestException(String message) { super(400, message); }
    public BadRequestException() { super(400, "Bad Request"); }
}
