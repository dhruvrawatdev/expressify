# 30 — Proxy Middleware

The proxy middleware forwards HTTP requests to another server and returns the response. Useful for:
- Routing `/api` requests to a backend service
- Adding auth/headers before forwarding
- Load balancing between multiple backends

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.proxy.ProxyMiddleware;
import io.github.dhruvrawatdev.expressify.middleware.proxy.ProxyOptions;

// Forward all /api requests to http://backend:8080
app.use("/api", ProxyMiddleware.create(
    ProxyOptions.builder()
        .target("http://backend-service:8080")
        .build()
));
```

A request to `GET /api/users` is forwarded to `GET http://backend-service:8080/api/users`.

---

## Path rewriting

```java
app.use("/api", ProxyMiddleware.create(
    ProxyOptions.builder()
        .target("http://backend:8080")
        .rewrite("/api", "")   // strip /api prefix before forwarding
        .build()
));
// GET /api/users → forwarded as GET /users
```

---

## Add headers before forwarding

```java
app.use(ProxyMiddleware.create(
    ProxyOptions.builder()
        .target("http://backend:8080")
        .on(ProxyOptions.OnEvents.builder()
            .proxyReq((proxyReq, req, res) -> {
                // Add auth header to forwarded request
                proxyReq.setHeader("X-Auth-Token", "internal-secret");
            })
            .build()
        )
        .build()
));
```

---

## Handle proxy errors

```java
app.use(ProxyMiddleware.create(
    ProxyOptions.builder()
        .target("http://backend:8080")
        .on(ProxyOptions.OnEvents.builder()
            .error((err, req, res) -> {
                res.status(502).json(Map.of("error", "Backend unavailable"));
            })
            .build()
        )
        .build()
));
```

---

## Options

| Option | Type | Description |
|---|---|---|
| `target(String)` | String | Backend URL to forward to |
| `rewrite(from, to)` | String, String | Rewrite path before forwarding |
| `on(OnEvents)` | OnEvents | Lifecycle callbacks |
| `timeout(long)` | ms | Connection timeout |

---

## Next steps

- [31 — Validator](31_validator.md)
- [04 — Middleware](04_middleware.md)
