# 35 — Exception Types

Expressify provides a set of built-in exception classes that integrate directly with the error handler. Throw any of them from a route or middleware — the framework routes them to your error handler automatically.

---

## Why use built-in exceptions?

When you throw an `HttpException` from a handler, your error handler receives both the exception and the HTTP status code in one object. You don't need to set the status code manually.

```java
// Without built-in exceptions — two steps
app.get("/secret", (req, res, next) -> {
    if (!isAdmin(req)) {
        res.status(403).send("Forbidden");  // have to set status yourself
        return;
    }
    res.send("Secret");
});

// With built-in exceptions — one step, cleaner
app.get("/secret", (req, res, next) -> {
    if (!isAdmin(req)) throw new ForbiddenException("Admins only");
    res.send("Secret");
});
```

---

## Exception hierarchy

```
RuntimeException
  └─ ExpressifyException          (base for all Expressify errors)
       └─ HttpException           (HTTP error with a status code)
            ├─ BadRequestException         (400)
            ├─ UnauthorizedException       (401)
            ├─ ForbiddenException          (403)
            ├─ NotFoundException           (404)
            └─ InternalServerErrorException (500)

RuntimeException
  └─ MulterException              (file upload errors — not an HttpException)
```

---

## `HttpException` — custom status + message

Use `HttpException` when none of the specific subclasses match:

```java
import io.github.dhruvrawatdev.expressify.exceptions.HttpException;

// Any status code
throw new HttpException(409, "Conflict: email already in use");
throw new HttpException(422, "Unprocessable Entity");
throw new HttpException(429, "Too Many Requests");

// With a cause (wraps an underlying exception)
try {
    db.save(entity);
} catch (Exception e) {
    throw new HttpException(500, "Database error", e);
}
```

Read the status code in your error handler:

```java
app.error((err, req, res, next) -> {
    if (err instanceof HttpException http) {
        res.status(http.getStatusCode()).json(Map.of("error", http.getMessage()));
    } else {
        res.status(500).json(Map.of("error", "Internal Server Error"));
    }
});
```

---

## `BadRequestException` — 400

The client sent invalid input (missing fields, wrong format, etc.):

```java
import io.github.dhruvrawatdev.expressify.exceptions.BadRequestException;

app.post("/users", (req, res, next) -> {
    Map<?, ?> body = req.body();
    if (body == null || !body.containsKey("email")) {
        throw new BadRequestException("email is required");
    }
    res.status(201).json(createUser(body));
});

// No-arg form uses default message "Bad Request"
throw new BadRequestException();
```

---

## `UnauthorizedException` — 401

The request has no valid credentials (not logged in):

```java
import io.github.dhruvrawatdev.expressify.exceptions.UnauthorizedException;

RouteHandler requireAuth = (req, res, next) -> {
    String token = req.get("Authorization");
    if (token == null) throw new UnauthorizedException("Login required");
    next.run();
};

app.get("/profile", requireAuth, (req, res) -> res.json(getProfile(req)));

// No-arg form uses default message "Unauthorized"
throw new UnauthorizedException();
```

**Difference between 401 and 403:** 401 means "you are not logged in". 403 means "you are logged in but don't have permission".

---

## `ForbiddenException` — 403

The client is authenticated but not allowed to do this:

```java
import io.github.dhruvrawatdev.expressify.exceptions.ForbiddenException;

app.delete("/users/:id", requireAuth, (req, res, next) -> {
    String requesterId = (String) req.locals().get("userId");
    String targetId    = req.param("id");

    if (!requesterId.equals(targetId) && !isAdmin(requesterId)) {
        throw new ForbiddenException("Cannot delete another user's account");
    }
    deleteUser(targetId);
    res.status(204).end();
});

// No-arg form uses default message "Forbidden"
throw new ForbiddenException();
```

---

## `NotFoundException` — 404

The requested resource does not exist:

```java
import io.github.dhruvrawatdev.expressify.exceptions.NotFoundException;

app.get("/users/:id", (req, res, next) -> {
    User user = db.findUser(req.param("id"));
    if (user == null) throw new NotFoundException("User not found");
    res.json(user);
});

// No-arg form uses default message "Not Found"
throw new NotFoundException();
```

---

## `InternalServerErrorException` — 500

An unexpected server-side error:

```java
import io.github.dhruvrawatdev.expressify.exceptions.InternalServerErrorException;

app.get("/report", (req, res, next) -> {
    try {
        byte[] pdf = pdfService.generate();
        res.send(pdf);
    } catch (Exception e) {
        throw new InternalServerErrorException("Failed to generate report", e);
    }
});

// No-arg form
throw new InternalServerErrorException();

// With message only
throw new InternalServerErrorException("Cache unavailable");

// With message + cause
throw new InternalServerErrorException("Cache unavailable", cacheException);
```

---

## `MulterException` — file upload errors

`MulterException` is thrown by the `Multer` middleware when an upload exceeds a limit or includes an unexpected field. It is **not** an `HttpException` — it has a `code` string instead of a status code.

```java
import io.github.dhruvrawatdev.expressify.middleware.multer.MulterException;

app.error((err, req, res, next) -> {
    if (err instanceof MulterException me) {
        String msg = switch (me.getCode()) {
            case MulterException.LIMIT_FILE_SIZE     -> "File is too large";
            case MulterException.LIMIT_FILE_COUNT    -> "Too many files";
            case MulterException.LIMIT_UNEXPECTED_FILE -> "Unexpected field: " + me.getField();
            default -> "Upload error: " + me.getCode();
        };
        res.status(400).json(Map.of("error", msg));
        return;
    }
    // ... other errors
});
```

### MulterException codes

| Code constant | When thrown |
|---|---|
| `LIMIT_FILE_SIZE` | File exceeds `maxSize` |
| `LIMIT_FILE_COUNT` | More files than `maxCount` |
| `LIMIT_FIELD_KEY` | Field name too long |
| `LIMIT_FIELD_VALUE` | Field value too long |
| `LIMIT_FIELD_COUNT` | Too many non-file fields |
| `LIMIT_UNEXPECTED_FILE` | Field name not in `allowedFields` |
| `LIMIT_PART_COUNT` | Total parts exceed limit |

---

## `ExpressifyException` — base class

All Expressify exceptions extend `ExpressifyException`. Use it in catch blocks when you want to handle any Expressify-originating error:

```java
app.error((err, req, res, next) -> {
    if (err instanceof HttpException http) {
        res.status(http.getStatusCode()).json(Map.of("error", http.getMessage()));
    } else if (err instanceof MulterException me) {
        res.status(400).json(Map.of("error", "Upload failed: " + me.getCode()));
    } else if (err instanceof ExpressifyException) {
        res.status(500).json(Map.of("error", "Framework error"));
    } else {
        res.status(500).json(Map.of("error", "Internal Server Error"));
    }
});
```

---

## Complete error handler pattern

A production-ready error handler that handles all built-in exception types:

```java
import io.github.dhruvrawatdev.expressify.exceptions.*;
import io.github.dhruvrawatdev.expressify.middleware.multer.MulterException;

app.error((err, req, res, next) -> {
    // HTTP exceptions (BadRequest, Unauthorized, Forbidden, NotFound, etc.)
    if (err instanceof HttpException http) {
        res.status(http.getStatusCode()).json(Map.of(
            "error", http.getMessage(),
            "status", http.getStatusCode()
        ));
        return;
    }

    // File upload errors
    if (err instanceof MulterException me) {
        res.status(400).json(Map.of(
            "error", "Upload failed",
            "code",  me.getCode()
        ));
        return;
    }

    // Unknown errors — log and return 500
    System.err.println("Unhandled error: " + err.getMessage());
    err.printStackTrace();
    res.status(500).json(Map.of("error", "Internal Server Error"));
});
```

---

## Throwing from middleware

Exceptions thrown from middleware work the same as from routes:

```java
RouteHandler requireJson = (req, res, next) -> {
    String ct = req.get("Content-Type");
    if (ct == null || !ct.contains("application/json")) {
        throw new BadRequestException("Content-Type must be application/json");
    }
    next.run();
};

app.post("/data", requireJson, (req, res) -> res.json(req.body()));
```

---

## Throwing from async handlers

Exceptions from async handlers are caught automatically:

```java
app.getAsync("/users/:id", (req, res) ->
    db.findUserAsync(req.param("id"))
        .thenAccept(user -> {
            if (user == null) throw new NotFoundException("User not found");
            res.json(user);
        })
);
```

Or use `next.error()` explicitly:

```java
app.get("/users/:id", (req, res, next) -> {
    try {
        User user = db.findUser(req.param("id"));
        if (user == null) next.error(new NotFoundException("User not found"));
        else res.json(user);
    } catch (Exception e) {
        next.error(new InternalServerErrorException("DB error", e));
    }
});
```

---

## Quick reference

| Class | Status | Package |
|---|---|---|
| `HttpException` | any | `exceptions` |
| `BadRequestException` | 400 | `exceptions` |
| `UnauthorizedException` | 401 | `exceptions` |
| `ForbiddenException` | 403 | `exceptions` |
| `NotFoundException` | 404 | `exceptions` |
| `InternalServerErrorException` | 500 | `exceptions` |
| `ExpressifyException` | n/a | `exceptions` |
| `MulterException` | n/a | `middleware.multer` |

All classes are in `io.github.dhruvrawatdev.expressify.*`.

---

## Next steps

- [07 — Error Handling](07_error_handling.md) — how error handlers work
- [36 — Rate Limit Header Parser](36_ratelimit_header_parser.md) — parse rate-limit headers from external APIs
