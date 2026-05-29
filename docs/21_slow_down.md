# 21 — Slow Down

Slow down is a gentler version of rate limiting. Instead of blocking requests with a 429 error, it adds an artificial delay to slow them down progressively.

This is more user-friendly — the user's requests still work, they just get slower after a threshold.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.slow_down.SlowDown;
import io.github.dhruvrawatdev.expressify.middleware.slow_down.SlowDownOptions;

app.use(SlowDown.configure(
    SlowDownOptions.builder()
        .windowMs(15 * 60 * 1000)   // 15-minute window
        .delayAfter(100)             // first 100 requests: no delay
        .delayMs(500)                // each request after 100: +500ms delay
        .maxDelayMs(20_000)          // cap delay at 20 seconds
        .build()
));
```

How it works:
- Requests 1–100: instant ✅
- Request 101: 500ms delay
- Request 102: 1000ms delay
- Request 103: 1500ms delay
- ...up to 20 seconds maximum

---

## Defaults

```java
app.use(SlowDown.defaults());
// delayAfter=5, delayMs=1000ms (1 second), windowMs=60000 (1 minute)
```

---

## Combine with rate limiter

Slow Down and Rate Limiter work well together:

```java
// Slow down after 50 requests
app.use(SlowDown.configure(
    SlowDownOptions.builder()
        .windowMs(60_000)
        .delayAfter(50)
        .delayMs(200)
        .build()
));

// Hard block after 100 requests
app.use(RateLimiter.configure(
    RateLimiterOptions.builder()
        .windowMs(60_000)
        .max(100)
        .build()
));
```

---

## All options

| Option | Type | Default | Description |
|---|---|---|---|
| `windowMs(long)` | ms | 60000 | Time window in milliseconds |
| `delayAfter(int)` | int | 5 | Free requests before slowing |
| `delayMs(long)` | ms | 1000 | Delay added per extra request |
| `maxDelayMs(long)` | ms | infinity | Maximum delay cap |

---

## Next steps

- [20 — Rate Limiter](20_rate_limiter.md)
- [04 — Middleware](04_middleware.md)
