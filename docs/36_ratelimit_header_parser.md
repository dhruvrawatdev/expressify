# 36 — Rate Limit Header Parser

`RateLimitHeaderParser` is a utility that reads the rate-limit headers sent back by an **external API** (GitHub, Twitter, OpenAI, etc.) and gives you a clean Java object with the limit, remaining requests, and reset time.

Use it when your server calls a third-party API and you need to know if you're close to its rate limit.

---

## Quick start

```java
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.RateLimitHeaderParser;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.ParsedRateLimit;

// Headers you received from an external API response
Map<String, String> headers = Map.of(
    "X-RateLimit-Limit",     "100",
    "X-RateLimit-Remaining", "42",
    "X-RateLimit-Reset",     "1716312000"   // Unix timestamp
);

ParsedRateLimit info = RateLimitHeaderParser.parse(headers);

System.out.println(info.getLimit());      // 100
System.out.println(info.getRemaining());  // 42
System.out.println(info.getUsed());       // 58  (derived: limit - remaining)
System.out.println(info.getReset());      // 2024-05-21T16:00:00Z  (Instant)
```

---

## Supported header formats

The parser automatically detects which format an API uses.

### IETF Draft-7 — combined header

```
RateLimit: limit=100, remaining=42, reset=30
```

```java
Map<String, String> headers = Map.of(
    "RateLimit", "limit=100, remaining=42, reset=30"
);
ParsedRateLimit info = RateLimitHeaderParser.parse(headers);
```

### Draft-6 — separate headers

```
RateLimit-Limit:     100
RateLimit-Remaining: 42
RateLimit-Reset:     1716312000
```

```java
Map<String, String> headers = Map.of(
    "RateLimit-Limit",     "100",
    "RateLimit-Remaining", "42",
    "RateLimit-Reset",     "1716312000"
);
```

### Legacy `X-RateLimit-*` (GitHub, GitLab, most REST APIs)

```
X-RateLimit-Limit:     5000
X-RateLimit-Remaining: 4987
X-RateLimit-Reset:     1372700873
```

```java
Map<String, String> headers = Map.of(
    "X-RateLimit-Limit",     "5000",
    "X-RateLimit-Remaining", "4987",
    "X-RateLimit-Reset",     "1372700873"
);
```

### Twitter-style `X-Rate-Limit-*`

```
X-Rate-Limit-Limit:     15
X-Rate-Limit-Remaining: 12
X-Rate-Limit-Reset:     1372700873
```

```java
Map<String, String> headers = Map.of(
    "X-Rate-Limit-Limit",     "15",
    "X-Rate-Limit-Remaining", "12",
    "X-Rate-Limit-Reset",     "1372700873"
);
```

### `Retry-After` fallback

If there is no reset header but there is a `Retry-After` header, it is used as the reset time:

```
Retry-After: 120   (seconds from now)
```

---

## `ParsedRateLimit` — the result object

| Method | Returns | Meaning |
|---|---|---|
| `getLimit()` | `int` | Max requests per window (-1 if unknown) |
| `getUsed()` | `int` | Requests already used (-1 if unknown) |
| `getRemaining()` | `int` | Requests left in the current window |
| `getReset()` | `Instant` | When the window resets (null if unknown) |

Fields that the API did not include come back as `-1` (integers) or `null` (Instant).

```java
ParsedRateLimit info = RateLimitHeaderParser.parse(headers);

if (info == null) {
    System.out.println("No rate-limit headers found");
    return;
}

int remaining = info.getRemaining();
Instant reset = info.getReset();

if (remaining < 10) {
    long secondsLeft = reset != null
        ? reset.getEpochSecond() - Instant.now().getEpochSecond()
        : 60;
    System.out.println("Almost at limit! Resets in " + secondsLeft + "s");
}
```

---

## Options — control how the reset value is interpreted

By default, the parser auto-detects the reset format. You can force a specific format:

```java
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.RateLimitHeaderParserOptions;
import io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser.RateLimitHeaderParserOptions.ResetType;

RateLimitHeaderParserOptions opts = RateLimitHeaderParserOptions.builder()
    .reset(ResetType.UNIX)       // treat reset as Unix timestamp in seconds
    .build();

ParsedRateLimit info = RateLimitHeaderParser.parse(headers, opts);
```

### `ResetType` values

| Value | Meaning |
|---|---|
| `AUTO` | Auto-detect (default): letters → date string, >1 billion → Unix timestamp, else seconds from now |
| `DATE` | HTTP-date string (RFC 1123 or ISO-8601) |
| `UNIX` | Unix epoch in seconds |
| `SECONDS` | Seconds from now |
| `MILLISECONDS` | Milliseconds from now |

---

## Returning `null`

`parse()` returns `null` when no recognised rate-limit headers are present in the map. Always check for null:

```java
ParsedRateLimit info = RateLimitHeaderParser.parse(headers);
if (info != null) {
    // safe to use info
}
```

---

## Real-world usage — GitHub API client

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.github.com/repos/octocat/Hello-World"))
    .header("Authorization", "token " + GITHUB_TOKEN)
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

// Convert response headers to a simple Map<String, String>
Map<String, String> headers = new HashMap<>();
response.headers().map().forEach((key, values) -> {
    if (!values.isEmpty()) headers.put(key, values.get(0));
});

ParsedRateLimit rateLimit = RateLimitHeaderParser.parse(headers);
if (rateLimit != null) {
    System.out.println("Remaining: " + rateLimit.getRemaining() + " / " + rateLimit.getLimit());
    System.out.println("Resets at: " + rateLimit.getReset());

    if (rateLimit.getRemaining() == 0) {
        long wait = rateLimit.getReset().getEpochSecond() - Instant.now().getEpochSecond();
        System.out.println("Rate limited! Wait " + wait + " seconds.");
    }
}

System.out.println(response.body());
```

---

## Usage in a proxy/gateway route

Forward a third-party API and expose the rate limit info to your frontend:

```java
app.get("/api/github/repo/:owner/:repo", (req, res, next) -> {
    String owner = req.param("owner");
    String repo  = req.param("repo");

    HttpResponse<String> ghResponse = githubClient.fetch("/repos/" + owner + "/" + repo);

    // Parse rate limit from GitHub's response
    Map<String, String> ghHeaders = toFlatMap(ghResponse.headers());
    ParsedRateLimit rl = RateLimitHeaderParser.parse(ghHeaders);

    res.json(Map.of(
        "data",      objectMapper.readValue(ghResponse.body(), Map.class),
        "rateLimit", rl != null ? Map.of(
            "remaining", rl.getRemaining(),
            "limit",     rl.getLimit(),
            "reset",     rl.getReset() != null ? rl.getReset().toString() : null
        ) : null
    ));
});
```

---

## Quick reference

| Method | Description |
|---|---|
| `RateLimitHeaderParser.parse(headers)` | Parse headers with auto-detection |
| `RateLimitHeaderParser.parse(headers, opts)` | Parse with explicit reset-type hint |
| `info.getLimit()` | Max requests per window (-1 = unknown) |
| `info.getUsed()` | Requests used (-1 = unknown) |
| `info.getRemaining()` | Requests remaining |
| `info.getReset()` | Reset time as `Instant` (null = unknown) |

The header map is **case-insensitive** — `"x-ratelimit-remaining"` and `"X-RateLimit-Remaining"` both work.

---

## Next steps

- [37 — Complete Application Example](37_complete_example.md) — a full REST API + WebSocket app using all the pieces
- [20 — Rate Limiter](20_rate_limiter.md) — rate-limit your own API (not reading external headers)
