# 17 — Session

Sessions let you store data for a user across multiple requests. For example, after a user logs in, you save their user ID in the session. On the next request, you read it back.

Sessions are stored **server-side** in memory. The client only receives a signed cookie with the session ID.

---

## Setup

```java
import io.github.dhruvrawatdev.expressify.middleware.session.SessionMiddleware;
import io.github.dhruvrawatdev.expressify.middleware.session.SessionOptions;

app.use(SessionMiddleware.configure(
    SessionOptions.builder()
        .secret("change-this-in-production")  // used to sign the session ID cookie
        .build()
));
```

---

## Reading and writing session data

```java
app.post("/login", (req, res) -> {
    // After verifying credentials...
    Map<String, Object> session = req.session();
    session.put("userId", 42);
    session.put("role", "admin");
    res.json(Map.of("logged", true));
});

app.get("/profile", (req, res) -> {
    Map<String, Object> session = req.session();
    Object userId = session.get("userId");
    if (userId == null) {
        res.status(401).send("Not logged in");
        return;
    }
    res.json(Map.of("userId", userId, "role", session.get("role")));
});

app.post("/logout", (req, res) -> {
    req.session().clear();  // destroy all session data
    res.json(Map.of("logged", false));
});
```

---

## Session shortcuts

```java
// Write
req.session("userId", 42);
req.session("cart", List.of("item1", "item2"));

// Read (typed)
Integer userId = req.<Integer>session("userId");
List   cart    = req.<List>session("cart");

// Read from map
Object userId = req.session().get("userId");
Object cart   = req.session().getOrDefault("cart", List.of());

// Remove one key
req.sessionRemove("cart");

// Clear everything
req.session().clear();

// Get session ID
String sid = req.getSessionId();
```

---

## All options

```java
SessionMiddleware.configure(
    SessionOptions.builder()
        .secret("my-secret-key")        // REQUIRED — used to sign the cookie
        .name("sid")                    // cookie name (default: "connect.sid")
        .maxAge(3600)                   // seconds before session expires (default: 24h)
        .httpOnly(true)                 // JS can't access the cookie (default: true)
        .secure(false)                  // require HTTPS? (default: false)
        .sameSite("Lax")                // CSRF protection: "Strict", "Lax", "None"
        .resave(false)                  // save session on every request even if unchanged
        .saveUninitialized(false)       // create session even before data is written
        .domain("example.com")          // cookie domain
        .path("/")                      // cookie path
        .build()
);
```

| Option | Default | Description |
|---|---|---|
| `secret` | (required) | Key used to sign the session cookie |
| `name` | `"connect.sid"` | Name of the session cookie |
| `maxAge` | 86400 (24h) | Session expiry in seconds |
| `httpOnly` | `true` | Prevent JavaScript from reading the cookie |
| `secure` | `false` | Only send cookie over HTTPS |
| `sameSite` | `"Lax"` | Cross-site cookie policy |
| `resave` | `true` | Save session on every request |
| `saveUninitialized` | `true` | Save empty sessions |

---

## Auth middleware example

```java
// Check session for every /api/* request
RouteHandler requireLogin = (req, res, next) -> {
    if (req.session().get("userId") == null) {
        res.status(401).json(Map.of("error", "Please log in"));
        return;
    }
    next.run();
};

app.use("/api", requireLogin);

app.get("/api/profile", (req, res) -> {
    Integer userId = (Integer) req.session().get("userId");
    res.json(Map.of("userId", userId));
});
```

---

## Multiple users, separate sessions

Each browser gets its own session cookie. Sessions are completely separate per user.

```java
// User A logs in → session A: {userId: 1}
// User B logs in → session B: {userId: 2}
// Each user's requests read from their own session map
```

---

## Important notes

- **In-memory storage** — sessions are lost if you restart the server
- **Not shared** between multiple server instances
- For production with multiple servers or restarts, use a database-backed session store (outside the scope of Expressify — use Redis or a database directly)

---

## Counter example (see it working)

```java
app.use(SessionMiddleware.configure(SessionOptions.builder()
    .secret("dev-secret")
    .saveUninitialized(true)
    .build()
));

app.get("/count", (req, res) -> {
    Map<String, Object> session = req.session();
    int count = (int) session.getOrDefault("count", 0);
    session.put("count", count + 1);
    res.json(Map.of("count", count + 1));
});
```

Visit `/count` multiple times — the number increases for each browser tab (session).

---

## Next steps

- [18 — Cookie Parser](18_cookie_parser.md)
- [19 — Cookie Session](19_cookie_session.md) — stateless alternative
