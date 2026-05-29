# 10 — Async Handlers

Async handlers let you do asynchronous work (like calling a database, reading a file, or calling an API) without blocking the server.

In Java, async work returns a `CompletableFuture`. Expressify handles the future for you — if it fails, the error is automatically sent to the error handler.

---

## Why use async?

- Your server can handle many requests at once instead of waiting for one to finish
- Database queries, API calls, and file reads don't block other requests
- Better performance under load

---

## AsyncSimpleHandler — most common

Two parameters, no `next`. Use for route handlers that do async work.

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncSimpleHandler;
import java.util.concurrent.CompletableFuture;

// Use getAsync(), postAsync(), etc.
app.getAsync("/users", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.runAsync(() -> {
        // Simulate async DB call
        List<String> users = db.findAll();
        res.json(users);
    })
);
```

---

## AsyncRouteHandler — with `next`

Three parameters including `next`. Use for async middleware that needs to call next.

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncRouteHandler;

// Async auth middleware
AsyncRouteHandler asyncAuth = (req, res, next) ->
    tokenService.validateAsync(req.get("Authorization"))
        .thenAccept(user -> {
            req.locals().put("user", user);
            try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
        });

app.use(asyncAuth);
```

---

## Named async methods

Expressify provides named `*Async()` methods for every HTTP verb:

```java
app.getAsync("/path",     handler);
app.postAsync("/path",    handler);
app.putAsync("/path",     handler);
app.patchAsync("/path",   handler);
app.deleteAsync("/path",  handler);
app.optionsAsync("/path", handler);
app.headAsync("/path",    handler);
app.allAsync("/path",     handler);
```

---

## Async GET, POST, PUT, PATCH, DELETE

```java
// GET — fetch data
app.getAsync("/posts/:id", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.runAsync(() -> {
        Post post = db.findPost(req.param("id"));
        if (post == null) throw new NotFoundException("Post not found");
        res.json(post);
    })
);

// POST — create data
app.postAsync("/posts", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.runAsync(() -> {
        Map<?,?> body = (Map<?,?>) req.body();
        Post created = db.createPost(body);
        res.status(201).json(created);
    })
);

// DELETE — remove data
app.deleteAsync("/posts/:id", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.runAsync(() -> {
        db.deletePost(req.param("id"));
        res.status(204).end();
    })
);
```

---

## Error handling — automatic

If the `CompletableFuture` is rejected (throws), Expressify **automatically** sends the error to the error handler. No try/catch needed in the route.

```java
app.getAsync("/data", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.failedFuture(new RuntimeException("Something broke"))
    // ↑ This automatically triggers the error handler
);

// The error handler catches it
app.error((err, req, res, next) -> {
    res.status(500).json(Map.of("error", err.getMessage()));
});
```

---

## Async with sync middleware

You can combine sync middleware with an async final handler:

### Array form

```java
RouteHandler authMw  = (req, res, next) -> { /* check auth */ next.run(); };
RouteHandler logMw   = (req, res, next) -> { /* log */        next.run(); };

app.getAsync("/data",
    new RouteHandler[]{ authMw, logMw },      // sync middleware first
    (AsyncSimpleHandler)(req, res) ->          // async handler last
        CompletableFuture.runAsync(() -> {
            res.json(fetchData());
        })
);
```

### List form

```java
app.getAsync("/data",
    List.of(authMw, logMw),
    (AsyncSimpleHandler)(req, res) ->
        CompletableFuture.runAsync(() -> res.json(fetchData()))
);
```

---

## useAsync — async middleware

```java
// Global async middleware
app.useAsync((AsyncRouteHandler)(req, res, next) ->
    sessionService.loadAsync(req.cookie("sid"))
        .thenAccept(session -> {
            req.locals().put("session", session);
            try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
        })
);

// Path-scoped async middleware
app.useAsync("/api", (AsyncRouteHandler)(req, res, next) ->
    rateLimiter.checkAsync(req.ip())
        .thenAccept(allowed -> {
            if (!allowed) res.status(429).send("Too many requests");
            else { try { next.run(); } catch (Exception e) { throw new RuntimeException(e); } }
        })
);
```

---

## Async error handler — `app.errorAsync()`

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncErrorHandler;

app.errorAsync((AsyncErrorHandler)(err, req, res, next) ->
    auditLogger.logAsync(err, req.path())
        .thenRun(() ->
            res.status(500).json(Map.of("error", err.getMessage()))
        )
);
```

---

## Async param handler

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncParamHandler;

app.param("userId", (AsyncParamHandler)(req, res, next, userId) ->
    userService.findByIdAsync(userId)
        .thenAccept(user -> {
            if (user == null) res.status(404).send("User not found");
            else {
                req.locals().put("user", user);
                try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
            }
        })
);

// The user is pre-loaded for all routes with :userId
app.get("/users/:userId",       (req, res) -> res.json(req.locals().get("user")));
app.put("/users/:userId",       (req, res) -> { /* update */ });
app.delete("/users/:userId",    (req, res) -> res.status(204).end());
```

---

## Custom thread pool

By default, `CompletableFuture.runAsync()` uses the common fork-join pool. Use a custom executor for more control:

```java
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;

Executor pool = Executors.newFixedThreadPool(10);

app.getAsync("/data", (AsyncSimpleHandler)(req, res) ->
    CompletableFuture.runAsync(() -> {
        res.json(fetchData());
    }, pool)
);
```

---

## Common patterns

### Chain multiple async operations

```java
app.getAsync("/dashboard/:userId", (AsyncSimpleHandler)(req, res) ->
    userService.findAsync(req.param("userId"))
        .thenCompose(user -> {
            if (user == null) throw new NotFoundException("User not found");
            return statsService.loadAsync(user.getId())
                .thenApply(stats -> Map.of("user", user, "stats", stats));
        })
        .thenAccept(data -> res.json(data))
);
```

### Run two things in parallel

```java
app.getAsync("/summary", (AsyncSimpleHandler)(req, res) -> {
    CompletableFuture<List<?>> usersFuture = userService.listAsync();
    CompletableFuture<Long>    countFuture = orderService.countAsync();

    return usersFuture.thenCombine(countFuture, (users, count) -> {
        res.json(Map.of("users", users, "orders", count));
        return null;
    });
});
```

---

## Casting async handlers inline

When Java cannot tell which interface you mean (ambiguity), cast explicitly:

```java
// Cast to the specific interface type
app.getAsync("/path", (AsyncSimpleHandler)(req, res) -> ...);
app.getAsync("/path", (AsyncRouteHandler)(req, res, next) -> ...);
```

Or use a typed variable:

```java
AsyncSimpleHandler handler = (req, res) ->
    CompletableFuture.runAsync(() -> res.send("hello"));

app.getAsync("/hello", handler);
```

---

## Summary

| Interface | Parameters | Use for |
|---|---|---|
| `AsyncSimpleHandler` | `(req, res)` | Async route handlers (most common) |
| `AsyncRouteHandler` | `(req, res, next)` | Async middleware that calls `next` |
| `AsyncErrorHandler` | `(err, req, res, next)` | Async error handlers |
| `AsyncParamHandler` | `(req, res, next, value)` | Async `app.param()` callbacks |

| Method | Description |
|---|---|
| `app.getAsync(path, handler)` | Async GET route |
| `app.postAsync(path, handler)` | Async POST route |
| `app.putAsync(path, handler)` | Async PUT route |
| `app.patchAsync(path, handler)` | Async PATCH route |
| `app.deleteAsync(path, handler)` | Async DELETE route |
| `app.useAsync(handler)` | Async global middleware |
| `app.useAsync(path, handler)` | Async path-scoped middleware |
| `app.errorAsync(handler)` | Async error handler |

---

## Next steps

- [11 — Param Handlers](11_param_handlers.md)
- [07 — Error Handling](07_error_handling.md)
