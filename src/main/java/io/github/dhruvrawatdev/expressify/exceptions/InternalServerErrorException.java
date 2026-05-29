package io.github.dhruvrawatdev.expressify.exceptions;

public class InternalServerErrorException extends HttpException {
    public InternalServerErrorException(String message) { super(500, message); }
    public InternalServerErrorException(String message, Throwable cause) { super(500, message, cause); }
    public InternalServerErrorException() { super(500, "Internal Server Error"); }
}
