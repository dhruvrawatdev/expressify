# 32 — Dev Error Handler

The dev error handler shows a detailed error page when something goes wrong. It's meant for **development only** — it shows stack traces which you should never expose in production.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.dev_error_handler.DevErrorHandler;

// Add this LAST, after all routes and middleware
app.error(DevErrorHandler.create());
```

When an error occurs, the browser shows:
- Error message
- Stack trace
- Status code
- Request details

---

## HTML vs JSON format

By default, the error page is HTML. The format is chosen automatically based on the `Accept` header.

```java
// Default — HTML error page
app.error(DevErrorHandler.create());
```

---

## Log errors to console

```java
import io.github.dhruvrawatdev.expressify.middleware.dev_error_handler.DevErrorHandlerOptions;

app.error(DevErrorHandler.create(
    DevErrorHandlerOptions.builder()
        .log(true)    // also log to stderr (default: false)
        .build()
));
```

---

## Use in development only

```java
String env = System.getenv("NODE_ENV");

if ("development".equals(env) || env == null) {
    // Dev: show full error details
    app.error(DevErrorHandler.create());
} else {
    // Production: safe error response
    app.error((err, req, res, next) -> {
        int code = 500;
        if (err instanceof HttpException httpEx) code = httpEx.getStatusCode();
        res.status(code).json(Map.of("error", "Something went wrong"));
    });
}
```

---

## Works with HttpException status codes

If you throw a `NotFoundException`, the dev error handler shows the 404 status instead of 500:

```java
throw new NotFoundException("User not found");
// → 404 error page with message "User not found"
```

---

## Next steps

- [07 — Error Handling](07_error_handling.md)
- [35 — Exception Types](35_exceptions.md)
