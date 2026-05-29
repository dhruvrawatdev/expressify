# 01 — Introduction to Expressify

## What is Expressify?

Expressify is a **Java 17+ web framework** that works just like **Express.js** from Node.js.

If you have ever used Express.js to build web servers in Node.js, you will feel right at home with Expressify. The API is designed to look and behave the same way — but written in Java.

If you are brand new to web frameworks, don't worry. This guide explains everything from scratch in simple English.

---

## Why use Expressify?

| Feature | What it means |
|---|---|
| **Express.js style** | Same `app.get()`, `app.use()`, `req`, `res` API you know from Node.js |
| **No XML config** | No Spring annotations, no web.xml, no servlet container needed |
| **Embedded server** | Undertow HTTP server is built in — just run `main()` and you're live |
| **Fast** | Built on Undertow, one of the fastest Java HTTP servers |
| **Everything included** | CORS, sessions, rate limiting, file uploads, WebSocket, Socket.IO — all built in |
| **Beginner friendly** | Simple, readable code — you can learn the whole framework in one day |

---

## How it compares to Express.js

```js
// Node.js Express.js
const app = express();
app.get('/hello', (req, res) => {
    res.send('Hello World');
});
app.listen(3000);
```

```java
// Java Expressify — almost identical!
Expressify app = new Expressify();
app.get("/hello", (req, res) -> {
    res.send("Hello World");
});
app.listen(3000);
```

The only difference is Java syntax. The concepts are exactly the same.

---

## What can you build with it?

- REST APIs (JSON APIs for mobile apps, frontends, etc.)
- Web servers with HTML template rendering
- Real-time apps with WebSocket or Socket.IO
- File upload servers
- Authentication servers
- Anything you can build with Express.js

---

## Requirements

- **Java 17 or later** (download from https://adoptium.net)
- **Maven 3.8+** (build tool)

---

## Next steps

- [02 — Installation & Quick Start](02_installation.md) — Add Expressify to your project and run your first server
- [03 — Routing](03_routing.md) — Learn how to handle HTTP requests
