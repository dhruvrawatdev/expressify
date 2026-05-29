# 04 — Middleware

## What is middleware?

Middleware is a function that runs **before** your route handler. It can:

- Read or modify the request
- Add headers to the response
- Check authentication
- Log requests
- Stop the request if something is wrong
- Pass to the next handler

Think of it like airport security — every passenger (request) must go through the checkpoints (middleware) before reaching their destination (your route handler).

---

## The handler signature

Every middleware and route handler receives three things:

```java
(req, res, next) -> {
    // req  = the request coming in
    // res  = the response you send out
    // next = a function to call to continue to the next step
}
```

### The three choices in a handler

```java
app.use((req, res, next) -> {

    // Choice 1: Pass to the next handler
    next.run();

    // Choice 2: Send a response and STOP the chain
    res.send("Blocked!");

    // Choice 3: Pass an error to the error handler
    next.error(new RuntimeException("Something went wrong"));
});
```

**Important:** You must do exactly ONE of these. If you don't send a response and don't call `next.run()`, the request will hang forever.

---

## Global middleware — `app.use()`

Runs for **every single request**, no matter what path or method.

```java
// Log every request
app.use((req, res, next) -> {
    System.out.println(req.method() + " " + req.path());
    next.run();  // must call next, or the request stops here
});

// Add a header to every response
app.use((req, res, next) -> {
    res.header("X-Powered-By", "MyApp");
    next.run();
});
```

---

## Path-scoped middleware — `app.use("/path", handler)`

Runs only when the request path **starts with** the given prefix.

```java
// Only runs for /api/* paths
app.use("/api", (req, res, next) -> {
    res.header("X-Api-Version", "2.0");
    next.run();
});

// Only runs for /admin/* paths
app.use("/admin", (req, res, next) -> {
    if (req.get("Authorization") == null) {
        res.status(401).send("Not authenticated");
        return;  // don't call next — stop here
    }
    next.run();
});
```

---

## Order matters

Middleware runs **in the order you register it**. Put global middleware before your routes.

```java
// Correct order:
app.use(Expressify.json());       // 1. Parse JSON bodies first
app.use(Expressify.cors());       // 2. Handle CORS
app.use(Expressify.morgan("dev")); // 3. Log requests

app.get("/users", ...);            // 4. Then your routes
app.post("/users", ...);

app.error((err, req, res, next) -> ...);  // 5. Error handler LAST
```

---

## Built-in middleware factories

Expressify includes many ready-to-use middleware. You just call the factory method and pass it to `app.use()`.

```java
app.use(Expressify.json());                  // parse JSON bodies
app.use(Expressify.urlencoded());            // parse form bodies
app.use(Expressify.cors());                  // CORS headers
app.use(Expressify.helmet());                // security headers
app.use(Expressify.compression());           // gzip responses
app.use(Expressify.morgan("dev"));           // request logging
app.use(ServeStatic.create("public"));       // serve static files
app.use(SessionMiddleware.configure(opts));  // sessions
app.use(RateLimiter.configure(opts));        // rate limiting
```

Each middleware is explained in detail in its own chapter.

---

## Multiple middleware in one `app.use()`

```java
// Register multiple at once (varargs)
app.use(mw1, mw2, mw3);

// Or a list
app.use(List.of(mw1, mw2, mw3));
```

---

## Middleware that short-circuits

Sometimes you want to stop the request without going to the next handler:

```java
// Block all requests without a token
app.use((req, res, next) -> {
    String token = req.get("X-Token");
    if (token == null) {
        res.status(401).json(Map.of("error", "Missing token"));
        return;  // don't call next.run() — the chain stops here
    }
    req.locals().put("token", token);  // save the token for later handlers
    next.run();
});
```

---

## `req.locals()` — passing data between middleware

Each request has a `locals` map that you can use to pass data from middleware to your route handler.

```java
// Middleware: load the user
app.use((req, res, next) -> {
    String userId = req.get("X-User-Id");
    if (userId != null) {
        req.locals().put("user", loadUser(userId));  // save user
    }
    next.run();
});

// Route handler: use the user
app.get("/profile", (req, res) -> {
    User user = (User) req.locals().get("user");
    res.json(Map.of("profile", user.getName()));
});
```

---

## Using middleware on specific routes

You can add middleware directly on a route instead of globally:

```java
RouteHandler authCheck = (req, res, next) -> {
    if (!isAuthenticated(req)) {
        res.status(401).send("Not allowed");
        return;
    }
    next.run();
};

// authCheck only runs for this route
app.get("/private", authCheck, (req, res) -> {
    res.send("Secret content");
});

// These routes don't require auth
app.get("/public", (req, res) -> res.send("Public content"));
```

---

## Sub-router middleware

Middleware registered on a `Router` only runs for that router's routes:

```java
Router apiRouter = new Router();

// Runs for ALL routes in this router
apiRouter.use((req, res, next) -> {
    res.header("X-API", "v2");
    next.run();
});

apiRouter.get("/users", (req, res) -> res.json(...));
apiRouter.get("/posts", (req, res) -> res.json(...));

app.use("/api", apiRouter);
// X-API header is only set for /api/users and /api/posts
```

---

## The `SimpleHandler` shortcut

If your middleware doesn't need to call `next.run()` explicitly (like a terminating route handler), use the two-parameter form:

```java
// Two-parameter — framework automatically calls next() if response not sent
app.use((req, res) -> {
    res.header("X-Timestamp", String.valueOf(System.currentTimeMillis()));
    // no need to call next — framework does it for you
});

// But if you DO send a response, the chain stops
app.use("/health", (req, res) -> {
    res.json(Map.of("status", "ok"));  // stops here — next() not called
});
```

---

## Next steps

- [05 — Request Object](05_request.md)
- [06 — Response Object](06_response.md)
- [07 — Error Handling](07_error_handling.md)
- [12 — Body Parser](12_body_parser.md) — Parse JSON, form data, text, binary
