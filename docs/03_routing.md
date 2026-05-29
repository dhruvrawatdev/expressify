# 03 — Routing

Routing means deciding what code runs when a user visits a URL with a specific HTTP method.

---

## HTTP Methods

Expressify supports every HTTP method:

```java
app.get("/path",     handler);   // read data
app.post("/path",    handler);   // create data
app.put("/path",     handler);   // replace data
app.patch("/path",   handler);   // update data partially
app.delete("/path",  handler);   // delete data
app.options("/path", handler);   // CORS preflight
app.head("/path",    handler);   // like GET but no body
app.all("/path",     handler);   // matches ANY method
```

---

## Handler Types

### SimpleHandler — most common, no `next` needed

```java
app.get("/hello", (req, res) -> {
    res.send("Hello!");
});
```

### RouteHandler — three parameters, use when you need `next`

```java
app.get("/hello", (req, res, next) -> {
    // do something, then pass to next handler
    next.run();
});
```

---

## Path Parameters

A **path parameter** is a part of the URL that changes. Write it with `:name`.

```java
app.get("/users/:id", (req, res) -> {
    String id = req.param("id");        // "42" if URL is /users/42
    res.json(Map.of("userId", id));
});

app.get("/users/:userId/posts/:postId", (req, res) -> {
    String userId = req.param("userId");
    String postId = req.param("postId");
    res.send(userId + " / " + postId);
});
```

Example requests:
- `GET /users/42` → `id = "42"`
- `GET /users/alice/posts/7` → `userId = "alice"`, `postId = "7"`

---

## Wildcard Paths

Use `*` to match the rest of the URL.

```java
app.get("/files/*", (req, res) -> {
    String path = req.param("0");    // captures everything after /files/
    res.send("Serving: " + path);
});
```

- `GET /files/images/photo.jpg` → `param("0") = "images/photo.jpg"`
- `GET /files/docs/readme.txt` → `param("0") = "docs/readme.txt"`

---

## Regex Constraints on Parameters

You can restrict a parameter to only match certain characters:

```java
// :id must be digits only
app.get("/items/:id([0-9]+)", (req, res) -> {
    res.send("Item: " + req.param("id"));
});
```

- `GET /items/123` → matches ✅
- `GET /items/abc` → 404 (doesn't match) ✅

---

## Trailing Slash and Double Slash

Expressify is lenient — these all work the same:
- `/users` and `/users/` both match `app.get("/users", ...)`
- `/users//profile` is treated as `/users/profile`

---

## `app.all()` — Match Any Method

```java
// Runs for GET, POST, PUT, DELETE, etc. on /status
app.all("/status", (req, res) -> {
    res.json(Map.of("method", req.method(), "ok", true));
});
```

Great for logging or checking auth on a path regardless of method.

---

## Middleware Chains on Routes

You can add middleware directly on a route. All middleware runs before the final handler.

### One middleware

```java
app.get("/admin", authMiddleware, (req, res) -> {
    res.send("Welcome admin");
});
```

### Two middleware

```java
app.get("/admin", authMiddleware, logMiddleware, (req, res) -> {
    res.send("Welcome admin");
});
```

### Array form — unlimited middleware

```java
RouteHandler[] guards = { authMiddleware, logMiddleware, rateLimitMiddleware };
app.get("/admin", guards, (req, res) -> {
    res.send("Welcome admin");
});
```

### List form — unlimited middleware

```java
app.get("/admin", List.of(authMiddleware, logMiddleware, rateLimitMiddleware), (req, res) -> {
    res.send("Welcome admin");
});
```

### Varargs form — all RouteHandlers

```java
app.get("/admin",
    authMiddleware,
    logMiddleware,
    (req, res, next) -> res.send("Welcome admin")
);
```

---

## Fluent Route Builder — `app.route()`

When you have multiple methods on the same path, use `app.route()` to avoid repeating the path:

```java
app.route("/api/users")
    .get((req, res) -> {
        // GET /api/users — list all users
        res.json(List.of("alice", "bob"));
    })
    .post((req, res) -> {
        // POST /api/users — create a user
        res.status(201).json(req.body());
    });

app.route("/api/users/:id")
    .get((req, res) -> res.json(Map.of("id", req.param("id"))))
    .put((req, res) -> res.json(Map.of("updated", true)))
    .delete((req, res) -> res.status(204).end());
```

---

## Sub-Routers — `Router`

Break your app into separate files using `Router`. A router is like a mini-app that handles a group of routes.

```java
import io.github.dhruvrawatdev.expressify.router.core.Router;

// Create a router for /users routes
Router usersRouter = new Router();
usersRouter.get("/",    (req, res) -> res.json(List.of("alice", "bob")));
usersRouter.get("/:id", (req, res) -> res.json(Map.of("id", req.param("id"))));
usersRouter.post("/",   (req, res) -> res.status(201).json(req.body()));
usersRouter.put("/:id", (req, res) -> res.json(Map.of("updated", true)));

// Mount the router at /users
// All routes above are now at /users/, /users/:id, etc.
app.use("/users", usersRouter);
```

Routers can also have their own middleware:

```java
Router adminRouter = new Router();
adminRouter.use((req, res, next) -> {
    // This middleware runs for every route in this router
    if (req.get("X-Admin-Token") == null) {
        res.status(403).send("Forbidden");
        return;
    }
    next.run();
});
adminRouter.get("/dashboard", (req, res) -> res.send("Admin Dashboard"));

app.use("/admin", adminRouter);
```

---

## 404 Not Found

If no route matches, Expressify automatically returns a `404 Not Found` response.

You can customize it:

```java
// Put this AFTER all your routes — it catches requests that matched nothing
app.use((req, res, next) -> {
    res.status(404).json(Map.of("error", "Not found: " + req.path()));
});
```

---

## Route Summary Table

| What you write | What it matches |
|---|---|
| `"/users"` | Exactly `/users` (and `/users/`) |
| `"/users/:id"` | `/users/anything` — captures as `id` |
| `"/users/:userId/posts/:postId"` | `/users/1/posts/2` — two captures |
| `"/files/*"` | `/files/a/b/c` — captures full remainder |
| `"/num/:n([0-9]+)"` | Only matches digits in that segment |
| `app.all("/path")` | Any HTTP method at `/path` |

---

## Next steps

- [04 — Middleware](04_middleware.md)
- [05 — Request Object](05_request.md)
- [06 — Response Object](06_response.md)
