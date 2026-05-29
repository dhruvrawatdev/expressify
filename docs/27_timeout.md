# 27 — Timeout

The timeout middleware cancels a request if it takes too long to respond. This prevents requests from hanging forever.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.timeout.Timeout;
import io.github.dhruvrawatdev.expressify.middleware.timeout.TimeoutOptions;

// Timeout after 5 seconds
app.use(Timeout.create(
    TimeoutOptions.builder()
        .timeout(5000)  // milliseconds
        .build()
));
```

Or with a string:

```java
app.use(Timeout.create(
    TimeoutOptions.builder()
        .timeout("5s")    // "5s", "500ms", "1m"
        .build()
));
```

---

## What happens on timeout

When a request times out, the middleware sets a flag on the request. Your route handler should check it:

```java
app.use(Timeout.create(TimeoutOptions.builder().timeout(3000).build()));

app.get("/slow", (req, res, next) -> {
    // Simulate slow work
    Thread.sleep(5000);

    // Check if we timed out during sleep
    Boolean timedOut = (Boolean) req.locals().get("timedout");
    if (timedOut != null && timedOut) {
        return;  // don't send response — timeout middleware already did
    }

    res.send("done");
});
```

---

## Custom timeout response

```java
app.use(Timeout.create(
    TimeoutOptions.builder()
        .timeout(5000)
        .respond(true)  // automatically send 503 on timeout (default: true)
        .build()
));
```

With `respond(true)` (default), the middleware automatically sends:
```
HTTP/1.1 503 Service Unavailable
```

---

## Clear timeout manually

```java
app.get("/fast", (req, res) -> {
    // Clear the timeout because this handler is fast
    Runnable clearTimeout = (Runnable) req.locals().get("clearTimeout");
    if (clearTimeout != null) clearTimeout.run();

    res.send("fast response");
});
```

---

## Per-route timeout

Different timeouts for different routes:

```java
// Default timeout: 5 seconds
app.use(Timeout.create(TimeoutOptions.builder().timeout(5000).build()));

// Upload endpoint: 60 seconds
Timeout longTimeout = Timeout.create(TimeoutOptions.builder().timeout(60_000).build());
app.post("/upload", longTimeout, uploadHandler);
```

---

## Important notes

- Timeout works on the Java thread level — it can't interrupt a CPU-bound loop
- It's most useful for detecting slow database queries, HTTP calls, or blocking I/O
- After a timeout fires, do NOT call `res.send()` — the middleware already responded

---

## Next steps

- [26 — Response Time](26_response_time.md)
- [28 — Method Override](28_method_override.md)
