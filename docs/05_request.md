# 05 — Request Object (`req`)

The `req` object contains everything about the incoming HTTP request. You receive it as the first parameter in every handler.

```java
app.get("/example", (req, res) -> {
    // req is the Request object — use it to read the request
});
```

---

## Request Method and URL

### `req.method()` — HTTP method

```java
req.method();  // "GET", "POST", "PUT", "PATCH", "DELETE", etc.
```

If you use `MethodOverride` middleware, this returns the overridden method. Use `req.originalMethod()` to get the real transport method.

### `req.path()` — URL path only (no query string)

```java
// For request: GET /users/42?sort=name
req.path();  // "/users/42"
```

### `req.originalUrl()` — full URL including query string

```java
req.originalUrl();  // "/users/42?sort=name"
```

### `req.baseUrl()` — the prefix where this router was mounted

```java
// If router mounted at /api, and handling /api/users
req.baseUrl();  // "/api"
```

---

## Path Parameters — `:param`

```java
app.get("/users/:id", (req, res) -> {
    req.param("id");     // "42" for GET /users/42
});

app.get("/posts/:category/:slug", (req, res) -> {
    req.param("category");  // "tech"
    req.param("slug");      // "my-post"
});

// Get ALL params as a Map
req.params();  // Map<String, String> e.g. {"id": "42"}
```

`req.params(name)` is the same as `req.param(name)` — both work.

---

## Query String — `?key=value`

```java
// For request: GET /search?q=java&page=2&limit=10

req.query("q");      // "java"
req.query("page");   // "2"
req.query("limit");  // "10"
req.query("missing"); // null

// With a default value
req.query("limit", "20");  // "10" if present, "20" if missing

// Get ALL query params as a Map
req.queryAll();  // {"q": "java", "page": "2", "limit": "10"}
```

---

## Request Body

To read the body you must have the appropriate body parser middleware registered. See [12 — Body Parser](12_body_parser.md).

### JSON body

```java
// After: app.use(Expressify.json())

Object raw = req.body();             // Object — could be Map or List
Map<?,?> body = (Map<?,?>) req.body(); // Cast to Map for JSON objects

// Get a specific field (works for urlencoded and JSON objects)
String name = req.body("name");
```

### URL-encoded form body

```java
// After: app.use(Expressify.urlencoded())

String name = req.body("name");   // "Alice" from "name=Alice&age=30"
Object all  = req.body();         // Map<String, List<String>>
```

### Plain text body

```java
// After: app.use(BodyParser.text())

String text = req.text();  // the raw text string
// or: String text = (String) req.body();
```

### Deserialize JSON to a class

```java
// After: app.use(Expressify.json())

UserDto user = req.bodyAs(UserDto.class);
System.out.println(user.getName());
```

---

## Headers

### `req.get(name)` — read a header (case-insensitive)

```java
req.get("Content-Type");     // "application/json"
req.get("content-type");     // same result — case-insensitive
req.get("Authorization");    // "Bearer abc123"
req.get("X-Custom-Header");  // null if missing
```

`req.header(name)` is the same as `req.get(name)`.

---

## Cookies

```java
// After: app.use(CookieParser.create())

req.cookie("session");      // value of the "session" cookie
req.cookies();              // Map<String, String> — all cookies

// Signed cookies (see cookie-parser chapter)
req.signedCookies();                    // Map<String, String>
req.signedCookie("sid", "my-secret");   // verify and get a signed cookie
```

---

## Session

```java
// After: app.use(SessionMiddleware.configure(opts))

Map<String, Object> session = req.session();  // the session map

session.put("userId", 42);           // write to session
session.get("userId");               // read from session — returns 42
session.getOrDefault("cart", List.of()); // read with default

// Typed shortcut
Integer userId = req.<Integer>session("userId");

// Write via shortcut
req.session("userId", 42);

// Remove a key
req.sessionRemove("cart");

// Get session ID
req.getSessionId();   // "s:abc123"
```

---

## Client Information

```java
req.ip();          // "192.168.1.1" — client IP address
req.ips();         // List<String> — proxy chain IPs (if trust proxy)
req.hostname();    // "example.com" from Host header
req.host();        // "example.com:3000" — with port
req.protocol();    // "http" or "https"
req.secure();      // true if HTTPS
req.subdomains();  // ["api", "v2"] for "v2.api.example.com"
```

---

## Content Negotiation

```java
// Is this a JSON request?
req.is("application/json");  // true/false
req.is("json");              // shorthand — also works
req.isJson();                // convenience method — same as req.is("json")

// What formats does the client accept?
req.accepts("json");         // "json" if client accepts it, null otherwise
req.accepts("html", "json"); // returns the best match
req.acceptsEncodings("gzip"); // "gzip" if client accepts gzip
req.acceptsCharsets("utf-8"); // "utf-8" if accepted
req.acceptsLanguages("en");   // "en" if accepted
```

---

## Cache Control

```java
req.fresh();   // true if the response would be 304 Not Modified
req.stale();   // opposite of fresh()
```

Use `fresh()` to avoid sending data the client already has cached.

---

## XHR (AJAX requests)

```java
req.xhr();  // true if request was made with XMLHttpRequest (AJAX)
```

---

## Range Requests

```java
// For partial content requests (like video streaming)
RangeResult result = req.range(fileSize);

if (result.isError()) {
    // Invalid range
}
if (result.isUnsatisfiable()) {
    // Range doesn't fit file
}

// Use the ranges
for (long[] range : result.ranges()) {
    long start = range[0];
    long end   = range[1];
}
```

---

## Request-local data — `req.locals()`

Share data between middleware and route handlers within the same request:

```java
// In middleware
req.locals().put("user", loadedUser);
req.locals().put("startTime", System.currentTimeMillis());

// In route handler
User user = (User) req.locals().get("user");
long start = (Long) req.locals().get("startTime");
```

This data only lives for the duration of the request.

---

## Uploaded files

```java
// After: Multer middleware (see chapter 22)

UploadedFile file = req.file("avatar");      // single file
List<UploadedFile> files = req.files("docs"); // multiple files

file.originalName();  // "photo.jpg"
file.size();          // file size in bytes
file.bytes();         // byte[] if using MemoryStorage
file.path();          // String file path if using DiskStorage
file.mimeType();      // "image/jpeg"
```

---

## Quick reference

| Method | Returns | Description |
|---|---|---|
| `req.method()` | String | HTTP method: GET, POST, etc. |
| `req.path()` | String | URL path without query string |
| `req.originalUrl()` | String | Full URL with query string |
| `req.param(name)` | String | URL path parameter |
| `req.params()` | Map | All path parameters |
| `req.query(name)` | String | Query string value |
| `req.queryAll()` | Map | All query string values |
| `req.body()` | Object | Parsed request body |
| `req.body(field)` | String | Single body field |
| `req.bodyAs(Class)` | T | Deserialize body to class |
| `req.get(name)` | String | Request header value |
| `req.cookie(name)` | String | Cookie value |
| `req.cookies()` | Map | All cookies |
| `req.session()` | Map | Session data |
| `req.ip()` | String | Client IP |
| `req.hostname()` | String | Request hostname |
| `req.protocol()` | String | "http" or "https" |
| `req.secure()` | boolean | Is HTTPS? |
| `req.xhr()` | boolean | Is AJAX request? |
| `req.is(type)` | boolean | Content type check |
| `req.accepts(type)` | String | Accept header check |
| `req.locals()` | Map | Request-scoped data store |
| `req.file(name)` | UploadedFile | Single uploaded file |
| `req.files(name)` | List | Multiple uploaded files |

---

## Next steps

- [06 — Response Object](06_response.md)
- [12 — Body Parser](12_body_parser.md)
- [17 — Session](17_session.md)
- [22 — File Uploads (Multer)](22_multer.md)
