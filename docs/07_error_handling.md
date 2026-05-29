# 07 — Error Handling

Error handling is how you deal with things that go wrong — a user not being logged in, a resource not found, a server crash.

---

## The error handler

Register an **error handler** using `app.error()`. Always put it **last**, after all your routes.

```java
// Four parameters — (err, req, res, next)
app.error((err, req, res, next) -> {
    res.status(500).json(Map.of("error", err.getMessage()));
});
```

The error handler only runs when an error occurs. Regular middleware has only three parameters `(req, res, next)` — the error handler has four `(err, req, res, next)`.

---

## How errors are triggered

### 1. Call `next.error(exception)` — most common

```java
app.get("/users/:id", (req, res, next) -> {
    User user = db.find(req.param("id"));
    if (user == null) {
        next.error(new RuntimeException("User not found"));
        return;
    }
    res.json(user);
});
```

### 2. Throw an exception — same effect as `next.error()`

```java
app.get("/users/:id", (req, res, next) -> {
    User user = db.find(req.param("id"));
    if (user == null) {
        throw new RuntimeException("User not found");  // same as next.error()
    }
    res.json(user);
});
```

### 3. Async errors — see [10 — Async Handlers](10_async_handlers.md)

---

## Built-in HTTP exceptions

Expressify provides ready-made exception classes for common HTTP errors. Use them to automatically set the right status code.

```java
import io.github.dhruvrawatdev.expressify.exceptions.*;
```

| Exception class | Status code | Use when |
|---|---|---|
| `BadRequestException` | 400 | Invalid input from client |
| `UnauthorizedException` | 401 | Not logged in / bad token |
| `ForbiddenException` | 403 | Logged in but no permission |
| `NotFoundException` | 404 | Resource doesn't exist |
| `InternalServerErrorException` | 500 | Something crashed |
| `HttpException` | custom | Base class — use any status code |

### Example — using built-in exceptions

```java
app.get("/users/:id", (req, res, next) -> {
    String id = req.param("id");
    User user = db.find(id);
    if (user == null) {
        throw new NotFoundException("User with id " + id + " not found");
    }
    res.json(user);
});

app.get("/admin", (req, res, next) -> {
    if (req.get("Authorization") == null) {
        throw new UnauthorizedException("Please log in");
    }
    if (!isAdmin(req)) {
        throw new ForbiddenException("Admin access required");
    }
    res.send("Welcome admin");
});
```

### Error handler that reads the status code

```java
app.error((err, req, res, next) -> {
    // Check if it's one of our built-in exceptions
    if (err instanceof HttpException httpEx) {
        res.status(httpEx.getStatusCode())
           .json(Map.of("error", httpEx.getMessage()));
    } else {
        // Unknown error — 500
        res.status(500).json(Map.of("error", "Internal Server Error"));
    }
});
```

### Custom `HttpException` with any status

```java
HttpException ex = new HttpException(422, "Validation failed");
next.error(ex);
```

---

## Multiple error handlers

You can chain multiple error handlers. Call `next.run()` inside an error handler to pass to the next one.

```java
// First: log the error
app.error((err, req, res, next) -> {
    System.err.println("Error: " + err.getMessage());
    next.run();  // pass to next error handler
});

// Second: send the response
app.error((err, req, res, next) -> {
    res.status(500).json(Map.of("error", err.getMessage()));
});
```

---

## Async error handler — `app.errorAsync()`

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncErrorHandler;

app.errorAsync((AsyncErrorHandler)(err, req, res, next) ->
    auditService.logErrorAsync(err)
        .thenRun(() -> res.status(500).json(Map.of("error", err.getMessage())))
);
```

---

## Error in middleware — `next.run()` inside error handler

After an error is caught and handled, you can call `next.run()` to continue to the next **error handler** (not regular handler):

```java
app.error((err, req, res, next) -> {
    if (err instanceof NotFoundException) {
        res.status(404).json(Map.of("error", err.getMessage()));
    } else {
        next.run();  // pass to next error handler
    }
});

app.error((err, req, res, next) -> {
    // This only runs for non-404 errors
    res.status(500).json(Map.of("error", "Server error"));
});
```

---

## Development error handler

Use `DevErrorHandler` during development — it shows a detailed error page with the stack trace. **Remove it before going to production.**

```java
// Only in development
app.error(DevErrorHandler.create());
```

This shows an HTML error page with:
- Error message
- Full stack trace
- Status code

For JSON format:

```java
import io.github.dhruvrawatdev.expressify.middleware.dev_error_handler.*;

app.error(DevErrorHandler.create(
    DevErrorHandlerOptions.builder()
        .log(true)  // also log to console
        .build()
));
```

See [32 — Dev Error Handler](32_dev_error_handler.md) for more details.

---

## Practical full example

```java
app.use(Expressify.json());

// Route that can throw different errors
app.post("/api/users", (req, res, next) -> {
    Map<?,?> body = (Map<?,?>) req.body();

    if (body == null || !body.containsKey("email")) {
        throw new BadRequestException("email is required");
    }

    String email = (String) body.get("email");
    if (db.emailExists(email)) {
        throw new HttpException(409, "Email already in use");
    }

    User user = db.createUser(email);
    res.status(201).json(Map.of("id", user.getId()));
});

// Error handler
app.error((err, req, res, next) -> {
    int code = 500;
    String message = "Internal Server Error";

    if (err instanceof HttpException httpEx) {
        code    = httpEx.getStatusCode();
        message = httpEx.getMessage();
    }

    res.status(code).json(Map.of(
        "error",   message,
        "status",  code,
        "path",    req.path()
    ));
});
```

---

## Quick reference

| Method | Description |
|---|---|
| `app.error((err, req, res, next) -> ...)` | Register error handler |
| `app.errorAsync(handler)` | Async error handler |
| `next.error(exception)` | Trigger error from any handler |
| `throw new Exception()` | Same as `next.error()` |
| `new NotFoundException(msg)` | 404 exception |
| `new BadRequestException(msg)` | 400 exception |
| `new UnauthorizedException(msg)` | 401 exception |
| `new ForbiddenException(msg)` | 403 exception |
| `new InternalServerErrorException(msg)` | 500 exception |
| `new HttpException(code, msg)` | Custom status code exception |
| `httpEx.getStatusCode()` | Read the status code from exception |
| `DevErrorHandler.create()` | Dev-only detailed error page |

---

## Next steps

- [08 — App Settings](08_settings.md)
- [10 — Async Handlers](10_async_handlers.md)
- [32 — Dev Error Handler](32_dev_error_handler.md)
