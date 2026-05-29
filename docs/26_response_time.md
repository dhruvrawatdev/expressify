# 26 — Response Time

Response time middleware adds an `X-Response-Time` header to every response showing how long the server took to process the request.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.response_time.ResponseTime;

app.use(ResponseTime.create());
```

Every response now includes:
```
X-Response-Time: 12.345ms
```

---

## Options

```java
import io.github.dhruvrawatdev.expressify.middleware.response_time.ResponseTimeOptions;

app.use(ResponseTime.create(
    ResponseTimeOptions.builder()
        .digits(3)           // decimal places (default: 3)
        .header("X-Time")    // custom header name (default: X-Response-Time)
        .suffix(true)        // include "ms" suffix (default: true)
        .build()
));
```

---

## Custom callback

```java
app.use(ResponseTime.create(
    ResponseTimeOptions.builder()
        .callback((req, res, time) -> {
            System.out.println(req.path() + " took " + time + "ms");
            res.header("X-Response-Time", time + "ms");
        })
        .build()
));
```

---

## Using the timing in routes

```java
app.use(ResponseTime.create());

app.get("/slow", (req, res) -> {
    // ... some work
    res.json(Map.of("data", "result"));
    // X-Response-Time is set automatically by the middleware
});
```

---

## Next steps

- [27 — Timeout](27_timeout.md)
- [04 — Middleware](04_middleware.md)
