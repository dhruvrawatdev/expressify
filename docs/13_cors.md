# 13 — CORS

CORS (Cross-Origin Resource Sharing) controls which websites can make API calls to your server from a browser.

**Example:** If your API is at `api.myapp.com` and your frontend is at `app.myapp.com`, the browser will block API calls unless you add CORS headers.

---

## Basic setup — allow all origins

```java
app.use(Expressify.cors());
```

This allows **any** website to call your API. Good for public APIs.

---

## Allow a specific origin

```java
import io.github.dhruvrawatdev.expressify.middleware.cors.CorsOptions;

app.use(Expressify.cors(
    CorsOptions.builder()
        .origin("https://myapp.com")  // only this website can call us
        .build()
));
```

---

## Allow multiple origins

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .origins("https://app1.com", "https://app2.com", "https://admin.myapp.com")
        .build()
));
```

---

## Dynamic origin check (function)

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .origin((requestOrigin) -> {
            // Return true to allow, false to deny
            List<String> allowed = List.of("https://app.com", "https://staging.app.com");
            return allowed.contains(requestOrigin);
        })
        .build()
));
```

---

## Allow credentials (cookies, Authorization header)

When your frontend sends cookies or an `Authorization` header, you must enable credentials:

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .origin("https://myapp.com")
        .credentials(true)  // allows cookies and auth headers
        .build()
));
```

**Important:** When `credentials(true)`, you cannot use a wildcard `*` origin — you must specify the exact origin.

---

## Custom allowed methods

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .methods("GET", "POST", "PUT", "DELETE")  // block PATCH, OPTIONS, etc.
        .build()
));
```

Default allowed methods: GET, HEAD, PUT, PATCH, POST, DELETE.

---

## Custom allowed headers

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .allowedHeaders("Content-Type", "Authorization", "X-Custom-Header")
        .build()
));
```

---

## Expose headers to the browser

Headers are hidden from JavaScript by default. Use `exposedHeaders` to let the browser read them:

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .exposedHeaders("X-Total-Count", "X-Page-Number")
        .build()
));
```

Now JavaScript can read `response.headers.get('X-Total-Count')`.

---

## Preflight cache — `maxAge`

Before making a cross-origin request, the browser sends an OPTIONS "preflight" request. Set how long the browser can cache the result:

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .maxAge(3600)  // browser caches preflight result for 1 hour
        .build()
));
```

---

## CORS only on specific routes

```java
// CORS on all routes
app.use(Expressify.cors());

// Or only on /api routes
app.use("/api", Expressify.cors(
    CorsOptions.builder()
        .origin("https://myapp.com")
        .build()
));
```

---

## Full example — typical production setup

```java
app.use(Expressify.cors(
    CorsOptions.builder()
        .origins("https://myapp.com", "https://staging.myapp.com")
        .methods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Content-Type", "Authorization")
        .exposedHeaders("X-Total-Count")
        .credentials(true)
        .maxAge(3600)
        .build()
));
```

---

## How CORS works under the hood

1. Browser sends request with `Origin: https://myapp.com` header
2. Expressify CORS middleware checks if that origin is allowed
3. If allowed, it adds `Access-Control-Allow-Origin: https://myapp.com` to the response
4. For methods like PUT/DELETE, browser first sends an `OPTIONS` preflight request
5. CORS middleware responds with 204 and the allowed methods/headers

---

## Quick reference

| Option | Type | Default | Description |
|---|---|---|---|
| `origin(String)` | String | `*` (all) | Allow a single specific origin |
| `origins(String...)` | varargs | — | Allow multiple origins |
| `origin(Function)` | Function | — | Dynamic origin check |
| `credentials(boolean)` | boolean | `true` | Allow cookies + auth headers |
| `methods(String...)` | varargs | all | Allowed HTTP methods |
| `allowedHeaders(String...)` | varargs | all | Allowed request headers |
| `exposedHeaders(String...)` | varargs | none | Headers visible to browser JS |
| `maxAge(int)` | seconds | none | Preflight cache duration |

---

## Next steps

- [14 — Helmet](14_helmet.md)
- [04 — Middleware](04_middleware.md)
