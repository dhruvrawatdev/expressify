package io.github.dhruvrawatdev.expressify.exceptions;

/**
 * HTTP error with a status code and message. Throw this from any handler and the
 * default error handler will respond with the correct HTTP status automatically.
 *
 * <pre>{@code
 * app.get("/secret", (req, res, next) -> {
 *     if (!req.session("user").equals("admin"))
 *         throw new HttpException(403, "Forbidden");
 *     res.send("secret");
 * });
 * }</pre>
 */
public class HttpException extends ExpressifyException {

    private final int statusCode;

    public HttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
