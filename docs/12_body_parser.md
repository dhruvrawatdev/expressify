# 12 — Body Parser

The body parser middleware reads the request body and makes it available on `req.body()`. Without it, `req.body()` returns `null`.

---

## JSON bodies

Use this for APIs that receive JSON (most REST APIs).

```java
app.use(Expressify.json());
```

Now in your routes:

```java
app.post("/users", (req, res) -> {
    // req.body() returns a Map for JSON objects, List for JSON arrays
    Map<?,?> body = (Map<?,?>) req.body();

    String name  = (String) body.get("name");
    Integer age  = (Integer) body.get("age");

    res.status(201).json(Map.of("created", name));
});
```

### JSON options

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.JsonOptions;

app.use(Expressify.json(
    JsonOptions.builder()
        .limit(512 * 1024)       // max body size = 512 KB (default is 100 KB)
        .strict(true)            // only accept objects and arrays (not raw values)
        .type("application/json") // custom content type to match
        .build()
));
```

### Deserialize to a class

```java
public class CreateUserRequest {
    public String name;
    public String email;
    public int age;
}

app.post("/users", (req, res) -> {
    CreateUserRequest body = req.bodyAs(CreateUserRequest.class);
    System.out.println(body.name);
    res.status(201).json(Map.of("created", true));
});
```

---

## URL-encoded form bodies

Use this for traditional HTML form submissions.

```java
app.use(Expressify.urlencoded());
```

Form data looks like: `name=Alice&age=30&city=New+York`

```java
app.post("/register", (req, res) -> {
    String name = req.body("name");   // "Alice"
    String age  = req.body("age");    // "30"
    String city = req.body("city");   // "New York"

    res.send("Registered: " + name);
});
```

### URL-encoded options

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.UrlencodedOptions;

app.use(Expressify.urlencoded(
    UrlencodedOptions.builder()
        .limit(100 * 1024)  // max 100 KB
        .build()
));
```

---

## Plain text bodies

Use this when the client sends plain text (not JSON).

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.BodyParser;

app.use(BodyParser.text());
```

```java
app.post("/message", (req, res) -> {
    String text = req.text();     // the raw text body
    // or: String text = (String) req.body();
    res.send("Received: " + text);
});
```

### Text options

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.TextOptions;

app.use(BodyParser.text(
    TextOptions.builder()
        .limit(10 * 1024)       // max 10 KB
        .defaultCharset("utf-8")
        .type("text/plain")
        .build()
));
```

---

## Raw binary bodies

Use this to receive raw bytes (file data, binary protocols, etc.).

```java
app.use(BodyParser.raw());
```

```java
app.post("/upload-raw", (req, res) -> {
    byte[] data = (byte[]) req.body();
    // process the bytes
    res.send("Received " + data.length + " bytes");
});
```

### Raw options

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.RawOptions;

app.use(BodyParser.raw(
    RawOptions.builder()
        .limit(10 * 1024 * 1024)  // max 10 MB
        .type("application/octet-stream")
        .build()
));
```

---

## Body verification — `BodyVerifier`

Use `BodyVerifier` to verify request signatures or checksums (like GitHub webhooks).

```java
import io.github.dhruvrawatdev.expressify.middleware.bodyparser.BodyVerifier;

app.use(Expressify.json(
    JsonOptions.builder()
        .verify(BodyVerifier.hmacSha256("your-webhook-secret", "X-Hub-Signature-256"))
        .build()
));
```

If verification fails, the middleware responds with `400 Bad Request`.

---

## Register multiple parsers

You can register all parsers at once — each only activates when the Content-Type matches:

```java
app.use(Expressify.json());         // handles: application/json
app.use(Expressify.urlencoded());   // handles: application/x-www-form-urlencoded
app.use(BodyParser.text());         // handles: text/plain
app.use(BodyParser.raw());          // handles: application/octet-stream
```

They don't conflict — each parser only fires when the Content-Type matches.

---

## What `req.body()` returns

| Content-Type | Returns |
|---|---|
| `application/json` (object) | `Map<String, Object>` |
| `application/json` (array) | `List<Object>` |
| `application/json` (string) | `String` |
| `application/x-www-form-urlencoded` | `Map<String, List<String>>` |
| `text/plain` | `String` |
| `application/octet-stream` | `byte[]` |
| Not parsed | `null` |

---

## Quick reference

| Method | What it does |
|---|---|
| `Expressify.json()` | Parse `application/json` bodies |
| `Expressify.json(opts)` | Parse JSON with custom options |
| `Expressify.urlencoded()` | Parse `application/x-www-form-urlencoded` |
| `Expressify.urlencoded(opts)` | Parse form data with custom options |
| `BodyParser.text()` | Parse `text/plain` bodies |
| `BodyParser.text(opts)` | Parse text with custom options |
| `BodyParser.raw()` | Parse `application/octet-stream` as `byte[]` |
| `BodyParser.raw(opts)` | Parse binary with custom options |
| `req.body()` | The parsed body |
| `req.body(fieldName)` | Single field from form or JSON object |
| `req.bodyAs(Class)` | Deserialize JSON to a Java class |
| `req.text()` | Text body (shortcut) |

---

## Next steps

- [05 — Request Object](05_request.md)
- [22 — File Uploads (Multer)](22_multer.md) — for multipart form data with files
