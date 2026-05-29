# 19 — Cookie Session

Cookie session stores session data **directly in the cookie** instead of on the server. The data is HMAC-signed so it can't be tampered with, but it is readable (not encrypted).

This is different from `SessionMiddleware` ([17 — Session](17_session.md)) which stores data on the server.

---

## When to use cookie session vs. server session

| | Cookie Session | Server Session |
|---|---|---|
| **Data stored** | In the cookie (client) | In memory (server) |
| **Size limit** | ~4 KB | Unlimited |
| **Survives restart** | ✅ Yes | ❌ No |
| **Multiple servers** | ✅ Works | ❌ Needs shared store |
| **Private data** | ⚠️ Visible (but signed) | ✅ Hidden from client |
| **Best for** | Small public data, preferences | Sensitive data, large data |

---

## Setup

```java
import io.github.dhruvrawatdev.expressify.middleware.cookie_session.CookieSession;
import io.github.dhruvrawatdev.expressify.middleware.cookie_session.CookieSessionOptions;

app.use(CookieSession.create(
    CookieSessionOptions.builder()
        .secret("my-hmac-signing-secret")  // REQUIRED
        .build()
));
```

---

## Reading and writing

```java
app.post("/preferences", (req, res) -> {
    // Write to cookie session
    req.session().put("theme", "dark");
    req.session().put("lang", "en");
    res.json(Map.of("saved", true));
});

app.get("/preferences", (req, res) -> {
    // Read from cookie session
    String theme = (String) req.session().getOrDefault("theme", "light");
    String lang  = (String) req.session().getOrDefault("lang", "en");
    res.json(Map.of("theme", theme, "lang", lang));
});

app.post("/clear-prefs", (req, res) -> {
    req.session().clear();
    res.json(Map.of("cleared", true));
});
```

The API is the same as `SessionMiddleware` — both use `req.session()`.

---

## All options

```java
CookieSessionOptions.builder()
    .secret("signing-secret")      // REQUIRED
    .name("session")               // cookie name (default: "expressify.session")
    .maxAge(86400000L)             // expiry in MILLISECONDS (default: 24h)
    .httpOnly(true)                // JS can't read cookie (default: true)
    .secure(false)                 // HTTPS only (default: false)
    .sameSite("Lax")               // cookie same-site policy
    .path("/")                     // cookie path
    .domain(null)                  // cookie domain (default: null = current domain)
    .build()
```

**Note:** `maxAge` is in **milliseconds** (unlike `SessionMiddleware` which uses seconds).

---

## Security note

The cookie data is **signed but not encrypted**. Anyone who has the cookie can read the values — they just can't modify them without being detected.

**Do not store:**
- Passwords
- Credit card numbers
- Other sensitive private data

**Good to store:**
- User preferences (theme, language)
- Shopping cart items
- Feature flags
- Non-sensitive user state

---

## Full example

```java
app.use(CookieSession.create(
    CookieSessionOptions.builder()
        .secret("s3cr3t-key-change-me")
        .maxAge(7 * 24 * 60 * 60 * 1000L)  // 7 days in ms
        .httpOnly(true)
        .sameSite("Lax")
        .build()
));

app.get("/", (req, res) -> {
    int visits = (int) req.session().getOrDefault("visits", 0);
    req.session().put("visits", visits + 1);
    res.send("You have visited " + (visits + 1) + " times");
});
```

---

## Next steps

- [17 — Session](17_session.md)
- [18 — Cookie Parser](18_cookie_parser.md)
