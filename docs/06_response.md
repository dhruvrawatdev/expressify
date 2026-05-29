# 06 — Response Object (`res`)

The `res` object is how you send a response back to the client. You receive it as the second parameter in every handler.

**Important rule:** You must send exactly **one** response per request. Calling two send methods will cause an error.

---

## Status Code

### `res.status(code)` — set the HTTP status code (chainable)

```java
res.status(200);           // OK
res.status(201);           // Created
res.status(400);           // Bad Request
res.status(401);           // Unauthorized
res.status(403);           // Forbidden
res.status(404);           // Not Found
res.status(500);           // Internal Server Error

// Chaining — set status AND send
res.status(201).json(Map.of("created", true));
res.status(404).send("Not found");
```

The default status is `200 OK` if you don't call `status()`.

### `res.sendStatus(code)` — send status code as the body text

```java
res.sendStatus(200);  // sends "OK"
res.sendStatus(404);  // sends "Not Found"
res.sendStatus(204);  // sends "No Content"
```

### `res.getStatus()` — read the current status code

```java
int code = res.getStatus();  // 200 by default
```

---

## Sending Responses

### `res.send(text)` — send plain text or HTML

```java
res.send("Hello World");         // text/plain
res.send("<h1>Hello</h1>");      // still text/plain unless you set type
```

### `res.json(data)` — send JSON

```java
res.json(Map.of("name", "Alice", "age", 30));
// → {"name":"Alice","age":30}

res.json(List.of(1, 2, 3));
// → [1,2,3]

res.json("hello");
// → "hello"

res.json(42);
// → 42

res.json(true);
// → true
```

Content-Type is automatically set to `application/json`.

### `res.jsonp(data)` — send JSONP

```java
// Client calls: GET /data?callback=myFunction
res.jsonp(Map.of("value", 42));
// → myFunction({"value":42})
```

### `res.send(bytes)` — send raw bytes

```java
byte[] imageData = Files.readAllBytes(imagePath);
res.type("image/jpeg");
res.send(imageData);
```

### `res.end()` — send empty response

```java
res.status(204).end();  // No Content — no body
```

---

## Redirects

### `res.redirect(url)` — redirect with 302

```java
res.redirect("/new-location");
res.redirect("https://example.com");
```

### `res.redirect(code, url)` — redirect with custom code

```java
res.redirect(301, "/permanent-new-location");  // permanent
res.redirect(307, "/temporary-redirect");       // temporary, keeps method
res.redirect(302, "/login");                    // default redirect
```

---

## Headers

### `res.header(name, value)` — set a response header (chainable)

```java
res.header("X-Custom-Header", "my-value");
res.header("Cache-Control", "no-cache");

// Chaining
res.header("X-One", "1").header("X-Two", "2").send("ok");
```

`res.set(name, value)` does the same thing.

### `res.set(map)` — set multiple headers at once

```java
res.set(Map.of(
    "X-Version", "2.0",
    "X-Region",  "us-east"
));
```

### `res.get(name)` — read a header you already set

```java
String ct = res.get("Content-Type");
```

### `res.removeHeader(name)` — remove a header

```java
res.removeHeader("X-Powered-By");
```

### `res.append(name, value)` — append to a header (adds to existing)

```java
res.append("Set-Cookie", "a=1; Path=/");
res.append("Set-Cookie", "b=2; Path=/");
```

---

## Content Type

### `res.type(type)` — set Content-Type

```java
res.type("text/html");
res.type("application/json");
res.type("text/plain");
res.type("image/jpeg");

// Shorthand extensions also work
res.type("html");
res.type("json");
res.type("txt");
```

`res.contentType(type)` does the same thing.

---

## Cookies

### `res.cookie(name, value)` — set a cookie

```java
res.cookie("session", "abc123");
```

### `res.cookie(name, value, options)` — with options

```java
import io.github.dhruvrawatdev.expressify.middleware.cookie_parser.CookieOptions;

res.cookie("session", "abc123",
    CookieOptions.builder()
        .httpOnly(true)         // JavaScript can't access it
        .secure(true)           // HTTPS only
        .maxAge(3600)           // expires in 1 hour (seconds)
        .sameSite("Strict")     // CSRF protection
        .path("/")              // cookie path
        .domain("example.com")  // cookie domain
        .build()
);
```

### `res.cookie(name, object)` — set a cookie from a Java object (JSON-serialized)

```java
// The object is serialized to JSON and stored in the cookie value
res.cookie("prefs", Map.of("theme", "dark", "lang", "en"));
```

### `res.clearCookie(name)` — delete a cookie

```java
res.clearCookie("session");
res.clearCookie("session", CookieOptions.builder().path("/").build());
```

---

## Special Header Helpers

### `res.vary(field)` — add to Vary header

```java
res.vary("Accept-Encoding");
res.vary("Origin");
```

### `res.links(map)` — set Link header for pagination

```java
res.links(Map.of(
    "next", "https://api.example.com/users?page=2",
    "last", "https://api.example.com/users?page=5"
));
// Sets: Link: <https://...?page=2>; rel="next", ...
```

### `res.location(url)` — set Location header

```java
res.location("/users/42");
res.location("https://example.com");
```

---

## File Responses

### `res.sendFile(path)` — serve a file

```java
res.sendFile("public/images/logo.png");
res.sendFile("/absolute/path/to/file.pdf");
```

Sets the correct Content-Type automatically.

### `res.download(path)` — force browser to download a file

```java
res.download("reports/data.csv");
res.download("reports/data.csv", "my-report.csv");  // custom download name
```

### `res.attachment(filename)` — set Content-Disposition header

```java
res.attachment("data.csv");
// Sets: Content-Disposition: attachment; filename="data.csv"
```

---

## Content Negotiation

### `res.format(callbacks)` — respond differently based on Accept header

```java
res.format(Map.of(
    "text/html",  (Runnable)(() -> res.send("<h1>Hello</h1>")),
    "application/json", (Runnable)(() -> res.json(Map.of("msg", "Hello")))
));
```

---

## Template Rendering

### `res.render(templateName)` — render a template

```java
res.render("home");  // renders templates/home.html (or .ftl, .pebble, etc.)
```

### `res.render(templateName, model)` — render with data

```java
res.render("profile", Map.of(
    "user", "Alice",
    "email", "alice@example.com",
    "role", "admin"
));
```

### `res.render(templateName, model, engineName)` — use a specific engine

```java
res.render("home", model, "pebble");     // use Pebble
res.render("home", model, "freemarker"); // use FreeMarker
```

See [09 — Template Engines](09_template_engines.md) for details.

---

## Advanced

### `res.write(bytes)` — write chunks (streaming)

```java
res.write("chunk 1".getBytes());
res.write("chunk 2".getBytes());
res.end();
```

### `res.pipe(inputStream)` — pipe a stream to the response

```java
InputStream fileStream = new FileInputStream("video.mp4");
res.type("video/mp4");
res.pipe(fileStream);
```

### `res.isCommitted()` — check if response was already sent

```java
if (!res.isCommitted()) {
    res.send("ok");
}
```

### `res.locals()` — response-level data store

```java
res.locals().put("renderTime", System.currentTimeMillis());
```

---

## Chaining

Most `res` methods return `res` itself, so you can chain them:

```java
res.status(201)
   .header("X-Created-Id", "42")
   .header("Location", "/users/42")
   .type("application/json")
   .json(Map.of("id", 42, "name", "Alice"));
```

---

## Quick reference

| Method | Description |
|---|---|
| `res.status(code)` | Set status code (chainable) |
| `res.sendStatus(code)` | Send status code as body text |
| `res.send(text)` | Send plain text response |
| `res.json(data)` | Send JSON response |
| `res.jsonp(data)` | Send JSONP response |
| `res.send(bytes)` | Send raw bytes |
| `res.end()` | Send empty response |
| `res.redirect(url)` | Redirect to URL (302) |
| `res.redirect(code, url)` | Redirect with specific code |
| `res.header(name, value)` | Set response header |
| `res.set(name, value)` | Same as `header()` |
| `res.get(name)` | Get a response header |
| `res.removeHeader(name)` | Remove a header |
| `res.append(name, value)` | Append to header |
| `res.type(type)` | Set Content-Type |
| `res.cookie(name, value)` | Set a cookie |
| `res.clearCookie(name)` | Delete a cookie |
| `res.vary(field)` | Add to Vary header |
| `res.links(map)` | Set Link header |
| `res.location(url)` | Set Location header |
| `res.sendFile(path)` | Serve a file |
| `res.download(path)` | Force file download |
| `res.attachment(name)` | Set download filename |
| `res.format(callbacks)` | Content negotiation |
| `res.render(name)` | Render a template |
| `res.render(name, model)` | Render template with data |
| `res.isCommitted()` | Check if response was sent |

---

## Next steps

- [07 — Error Handling](07_error_handling.md)
- [09 — Template Engines](09_template_engines.md)
- [05 — Request Object](05_request.md)
