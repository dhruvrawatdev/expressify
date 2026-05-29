# 18 — Cookie Parser

Cookie parser reads the cookies from the request header and makes them available on `req.cookie()` and `req.cookies()`.

Without cookie parser, `req.cookie()` always returns `null`.

---

## Setup

```java
import io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieParser;

app.use(CookieParser.create());
```

---

## Reading cookies

```java
app.get("/profile", (req, res) -> {
    String session = req.cookie("session");   // single cookie by name
    String theme   = req.cookie("theme");     // another cookie

    if (session == null) {
        res.status(401).send("No session");
        return;
    }

    res.json(Map.of("session", session, "theme", theme));
});
```

### Get all cookies as a Map

```java
Map<String, String> allCookies = req.cookies();
// {"session": "abc123", "theme": "dark", "lang": "en"}
```

---

## Setting cookies in responses

Use `res.cookie()` — it works **without** cookie parser (cookie parser is for reading, not writing):

```java
app.post("/login", (req, res) -> {
    // ... verify credentials ...
    res.cookie("session", "abc123");
    res.json(Map.of("ok", true));
});
```

### Cookie options

```java
import io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieOptions;

res.cookie("session", "abc123",
    CookieOptions.builder()
        .httpOnly(true)          // JS cannot read it
        .secure(true)            // HTTPS only
        .maxAge(3600)            // expires in 1 hour (seconds)
        .sameSite("Strict")      // Strict, Lax, or None
        .path("/")               // cookie path
        .domain("example.com")   // cookie domain
        .build()
);
```

### Clearing a cookie

```java
res.clearCookie("session");
res.clearCookie("session", CookieOptions.builder().path("/").build());
```

---

## Signed cookies

Signed cookies store a cryptographic signature alongside the value. If someone tampers with the cookie, the signature check fails and the value is rejected.

### Setup with a secret

```java
app.use(CookieParser.create("my-signing-secret"));
```

### Setting a signed cookie

```java
res.cookie("user", "alice", CookieOptions.builder().signed(true).build());
// The cookie value is: alice.s3cr3tSignature
```

### Reading a signed cookie

```java
// Method 1: access signed cookies map (only verified ones appear here)
Map<String, String> signed = req.signedCookies();
String user = signed.get("user");  // "alice" if valid, null if tampered

// Method 2: verify with specific secret
String user = req.signedCookie("user", "my-signing-secret");  // "alice" or null
```

---

## Store complex data in a cookie

```java
// Store a Java object — it's JSON-serialized automatically
res.cookie("prefs", Map.of("theme", "dark", "lang", "en"));
res.cookie("cart",  List.of("item-1", "item-2"));
```

Reading complex cookies requires JSON deserialization (you handle this yourself):

```java
import com.fasterxml.jackson.databind.ObjectMapper;

String prefsJson = req.cookie("prefs");
Map prefs = new ObjectMapper().readValue(prefsJson, Map.class);
```

---

## Full example

```java
app.use(CookieParser.create("my-secret"));  // with signing support

// Login — set a signed session cookie
app.post("/login", (req, res) -> {
    // verify credentials...
    res.cookie("session", "user-42",
        CookieOptions.builder()
            .httpOnly(true)
            .maxAge(86400)  // 1 day
            .signed(true)
            .build()
    );
    res.json(Map.of("ok", true));
});

// Auth middleware — check the signed cookie
app.use("/api", (req, res, next) -> {
    Map<String, String> signed = req.signedCookies();
    if (!signed.containsKey("session")) {
        res.status(401).send("Please log in");
        return;
    }
    req.locals().put("sessionValue", signed.get("session"));
    next.run();
});

// Logout — clear the cookie
app.post("/logout", (req, res) -> {
    res.clearCookie("session");
    res.json(Map.of("ok", true));
});
```

---

## Quick reference

| Method | Description |
|---|---|
| `CookieParser.create()` | Parse cookies (unsigned) |
| `CookieParser.create(secret)` | Parse cookies + verify signed cookies |
| `req.cookie(name)` | Get cookie value by name |
| `req.cookies()` | Get all cookies as Map |
| `req.signedCookies()` | Get all signed (verified) cookies |
| `req.signedCookie(name, secret)` | Get and verify a signed cookie |
| `res.cookie(name, value)` | Set a cookie |
| `res.cookie(name, value, opts)` | Set a cookie with options |
| `res.clearCookie(name)` | Delete a cookie |

---

## Next steps

- [19 — Cookie Session](19_cookie_session.md)
- [17 — Session](17_session.md)
