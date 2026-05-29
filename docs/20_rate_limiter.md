# 20 — Rate Limiter

Rate limiting prevents users from making too many requests in a short time. It protects your API from abuse, brute-force attacks, and denial-of-service.

---

## Basic setup

```java
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimiter;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimiterOptions;

// Allow 100 requests per 15 minutes per IP address
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(15 * 60 * 1000)   // 15 minutes in milliseconds
        .max(100)                    // max 100 requests per window
        .build()
));
```

When the limit is exceeded, the client gets:
- Status: `429 Too Many Requests`
- Body: `"Too many requests, please try again later."`

---

## Defaults

```java
app.use(RateLimiter.defaults());
// Same as: windowMs=60000 (1 minute), max=100 requests
```

---

## Custom message

```java
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(10)
        .message("Slow down! Too many requests.")
        .build()
));
```

---

## Apply only to specific routes

```java
// Global: applies to all routes
app.use(RateLimiter.configure(
    RateLimiterOptions.builder().windowMs(60_000).max(100).build()
));

// Only for login route — stricter limit
RateLimiter loginLimit = RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(15 * 60 * 1000)  // 15 minutes
        .max(5)                     // only 5 login attempts per window
        .message("Too many login attempts. Try again in 15 minutes.")
        .build()
);

app.post("/login", loginLimit, (req, res) -> {
    // handle login
});
```

---

## Skip certain requests

```java
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(50)
        .skip(req -> req.get("X-Internal-Service") != null) // skip internal calls
        .build()
));
```

---

## Custom key (rate limit by user ID instead of IP)

```java
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(100)
        .keyGenerator(req -> {
            // Rate limit by user ID if logged in, otherwise by IP
            Object userId = req.locals().get("userId");
            return userId != null ? userId.toString() : req.ip();
        })
        .build()
));
```

---

## Rate limit headers

After each request, Expressify adds headers so clients can see their limit:

```
RateLimit-Limit: 100
RateLimit-Remaining: 87
RateLimit-Reset: 1716893000
```

(Also available as `X-RateLimit-*` legacy headers.)

### Read the limit info from the route

```java
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimitInfo;

app.get("/data", (req, res) -> {
    RateLimitInfo info = (RateLimitInfo) req.locals().get("rateLimit");
    if (info != null) {
        System.out.println("Remaining: " + info.remaining());
    }
    res.send("data");
});
```

---

## Custom handler when limit exceeded

```java
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(10)
        .handler((req, res, next, options) -> {
            res.status(429).json(Map.of(
                "error", "Rate limit exceeded",
                "retryAfter", "60 seconds"
            ));
        })
        .build()
));
```

---

## Pluggable store

By default, limits are tracked in memory (per JVM instance). For multiple server instances, implement the `RateLimitStore` interface with a Redis or database backend.

```java
import io.github.dhruvrawatdev.expressify.middleware.ratelimit.RateLimitStore;

RateLimitStore redisStore = new MyRedisRateLimitStore(redisClient);

app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(100)
        .store(redisStore)
        .build()
));
```

---

## All options

| Option | Type | Default | Description |
|---|---|---|---|
| `windowMs(long)` | ms | 60000 | Time window in milliseconds |
| `max(int)` | int | 100 | Max requests per window |
| `message(String)` | String | "Too many requests..." | 429 response message |
| `skip(Function)` | Predicate | none | Function to skip rate limiting |
| `keyGenerator(Function)` | Function | IP address | Key for tracking per-user |
| `handler(handler)` | Handler | 429 response | Custom exceeded handler |
| `store(RateLimitStore)` | Interface | in-memory | Custom storage backend |
| `standardHeaders(boolean)` | boolean | true | Send `RateLimit-*` headers |
| `legacyHeaders(boolean)` | boolean | true | Send `X-RateLimit-*` headers |

---

## Next steps

- [21 — Slow Down](21_slow_down.md)
- [04 — Middleware](04_middleware.md)
