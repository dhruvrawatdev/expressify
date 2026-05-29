package io.github.dhruvrawatdev.expressify.exceptions;

public class UnauthorizedException extends HttpException {
    public UnauthorizedException(String message) { super(401, message); }
    public UnauthorizedException() { super(401, "Unauthorized"); }
}
