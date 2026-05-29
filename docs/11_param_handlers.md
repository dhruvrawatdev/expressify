# 11 — Param Handlers

A param handler is a callback that runs **before** any route handler when a specific URL parameter appears in the matched route. It mirrors Express.js's `app.param()`.

Use it to load a resource once and share it across all routes that use that parameter — instead of repeating the same database lookup in every route.

---

## Basic usage — `app.param(name, handler)`

```java
// Load a user whenever ":userId" appears in any matched route
app.param("userId", (req, res, next, userId) -> {
    User user = db.findUser(userId);
    if (user == null) {
        res.status(404).json(Map.of("error", "User not found"));
        return;   // do NOT call next
    }
    req.locals().put("user", user);  // save for later handlers
    next.run();                       // proceed to the route
});

// Now every route with :userId gets the user pre-loaded
app.get("/users/:userId",           (req, res) -> {
    User user = (User) req.locals().get("user");
    res.json(user);
});

app.get("/users/:userId/posts",     (req, res) -> {
    User user = (User) req.locals().get("user");
    res.json(db.getPostsByUser(user.getId()));
});

app.put("/users/:userId",           (req, res) -> {
    User user = (User) req.locals().get("user");
    // update user...
    res.json(Map.of("updated", true));
});

app.delete("/users/:userId",        (req, res) -> {
    User user = (User) req.locals().get("user");
    db.delete(user.getId());
    res.status(204).end();
});
```

The param handler runs **once per matched route** for each unique parameter name.

---

## Param handler signature

```java
import io.github.dhruvrawatdev.expressify.router.handler.ParamHandler;

app.param("paramName", (ParamHandler)(req, res, next, value) -> {
    // req    = the request
    // res    = the response
    // next   = call next.run() to proceed, or next.error() to abort
    // value  = the string value of the parameter, e.g. "42" for /users/42
});
```

---

## Validation example

Validate that a parameter matches your expectations before the route runs:

```java
// Only allow numeric IDs
app.param("id", (req, res, next, id) -> {
    try {
        Integer.parseInt(id);  // validate it's a number
        next.run();
    } catch (NumberFormatException e) {
        res.status(400).json(Map.of("error", "id must be a number"));
    }
});

app.get("/items/:id", (req, res) -> res.send("item:" + req.param("id")));
```

---

## Multiple parameters

Register separate handlers for each parameter:

```java
app.param("authorId", (req, res, next, authorId) -> {
    req.locals().put("author", db.findAuthor(authorId));
    next.run();
});

app.param("postId", (req, res, next, postId) -> {
    req.locals().put("post", db.findPost(postId));
    next.run();
});

// Both run for this route — author and post both pre-loaded
app.get("/authors/:authorId/posts/:postId", (req, res) -> {
    Author author = (Author) req.locals().get("author");
    Post   post   = (Post)   req.locals().get("post");
    res.json(Map.of("author", author, "post", post));
});
```

---

## Async param handler

```java
import io.github.dhruvrawatdev.expressify.router.handler.AsyncParamHandler;

app.param("userId", (AsyncParamHandler)(req, res, next, userId) ->
    userService.findByIdAsync(userId)
        .thenAccept(user -> {
            if (user == null) {
                res.status(404).json(Map.of("error", "User not found"));
                return;
            }
            req.locals().put("user", user);
            try { next.run(); } catch (Exception e) { throw new RuntimeException(e); }
        })
);
```

---

## How param handlers fire

- The param handler runs **once** even if the parameter appears multiple times in a single route pattern
- It runs **before** any middleware registered specifically on that route
- If multiple routes use the same parameter, the param handler fires on **every matching route** separately

---

## Practical full example

```java
Expressify app = new Expressify();
app.use(Expressify.json());

// Pre-load the product for all /products/:productId routes
app.param("productId", (req, res, next, id) -> {
    Product product = catalog.find(id);
    if (product == null || !product.isActive()) {
        res.status(404).json(Map.of("error", "Product not found"));
        return;
    }
    req.locals().put("product", product);
    next.run();
});

app.route("/products/:productId")
    .get((req, res) -> {
        Product p = (Product) req.locals().get("product");
        res.json(p);
    })
    .put((req, res) -> {
        Product p = (Product) req.locals().get("product");
        // update p with req.body()...
        res.json(Map.of("updated", true));
    })
    .delete((req, res) -> {
        Product p = (Product) req.locals().get("product");
        catalog.remove(p.getId());
        res.status(204).end();
    });

app.error((err, req, res, next) ->
    res.status(500).json(Map.of("error", err.getMessage())));

app.listen(3000);
```

---

## Summary

| Method | Description |
|---|---|
| `app.param(name, ParamHandler)` | Sync param callback |
| `app.param(name, AsyncParamHandler)` | Async param callback |
| Param handler runs | Before route handlers, when the named param is in the matched route |
| Call `next.run()` | To proceed to the route handler |
| Call `next.error(e)` or `throw` | To reject the request with an error |
| Don't call `next` | To stop and send your own response (e.g. 404) |

---

## Next steps

- [03 — Routing](03_routing.md) — Path parameters
- [10 — Async Handlers](10_async_handlers.md)
- [07 — Error Handling](07_error_handling.md)
