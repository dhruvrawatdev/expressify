# 29 — VHost (Virtual Hosts)

Virtual host routing lets you serve different content based on the **hostname** in the request. One server, multiple domain names.

---

## Basic usage

```java
import io.github.dhruvrawatdev.expressify.middleware.vhost.VHost;

// Route requests for api.example.com
app.use(VHost.use("api.example.com",
    (req, res, next) -> res.json(Map.of("api", "v1"))
));

// Route requests for www.example.com
app.use(VHost.use("www.example.com",
    (req, res, next) -> res.send("<h1>Welcome to my website!</h1>")
));

// Default — requests not matching any vhost
app.get("/", (req, res) -> res.send("Default"));
```

---

## Using a sub-application per host

```java
// Create separate mini-apps for each host
Expressify apiApp = new Expressify();
apiApp.use(Expressify.json());
apiApp.get("/users", (req, res) -> res.json(List.of("alice", "bob")));

Expressify adminApp = new Expressify();
adminApp.get("/dashboard", (req, res) -> res.send("Admin Dashboard"));

// Mount them by hostname
Router apiRouter = new Router();
apiRouter.get("/users", (req, res) -> res.json(List.of("alice", "bob")));
app.use(VHost.use("api.example.com", (req, res, next) -> {
    // Handle api.example.com requests here
    res.json(Map.of("host", "api"));
}));
```

---

## Wildcard subdomains

Match any subdomain with `*`:

```java
app.use(VHost.use("*.example.com", (req, res, next) -> {
    // Get the matched subdomain
    import io.github.dhruvrawatdev.expressify.middleware.vhost.VHostInfo;

    VHostInfo vhost = (VHostInfo) req.locals().get("vhost");
    String subdomain = vhost.get(0);  // "api" for "api.example.com"

    res.send("Subdomain: " + subdomain);
}));
```

---

## VHostInfo — captured wildcard values

```java
app.use(VHost.use("*.*.example.com", (req, res, next) -> {
    VHostInfo vhost = (VHostInfo) req.locals().get("vhost");

    String first  = vhost.get(0);       // first wildcard segment
    String second = vhost.get(1);       // second wildcard segment
    String host   = vhost.hostname();   // full hostname

    res.send(first + " / " + second + " @ " + host);
}));
```

For a request to `staging.api.example.com`:
- `vhost.get(0)` → `"staging"`
- `vhost.get(1)` → `"api"`

---

## Multiple handlers per host

```java
app.use(VHost.use("api.example.com",
    authMiddleware,         // first: check auth
    logMiddleware,          // second: log
    (req, res, next) -> res.json(Map.of("ok", true))  // final: respond
));
```

---

## Testing vhosts locally

In production, DNS routes different hostnames to your server. For local testing, edit your hosts file:

```
# /etc/hosts (Linux/Mac) or C:\Windows\System32\drivers\etc\hosts (Windows)
127.0.0.1  api.example.com
127.0.0.1  www.example.com
```

Then access `http://api.example.com:3000` in your browser.

---

## Quick reference

```java
VHost.use(pattern, handlers...)  // match exact or wildcard hostname

// In handler: get matched wildcard parts
VHostInfo info = (VHostInfo) req.locals().get("vhost");
info.get(0)         // first matched segment
info.hostname()     // full hostname
```

---

## Next steps

- [30 — Proxy](30_proxy.md)
- [04 — Middleware](04_middleware.md)
