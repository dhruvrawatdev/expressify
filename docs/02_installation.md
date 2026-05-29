# 02 — Installation & Quick Start

## Step 1 — Add the dependency

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>io.github.dhruvrawatdev</groupId>
    <artifactId>expressify</artifactId>
    <version>2.1.0</version>
</dependency>
```

### Gradle (`build.gradle`)

```groovy
implementation 'io.github.dhruvrawatdev:expressify:2.1.0'
```

---

## Step 2 — Write your first server

Create a file called `Main.java`:

```java
import io.github.dhruvrawatdev.expressify.Expressify;

public class Main {
    public static void main(String[] args) {
        Expressify app = new Expressify();

        app.get("/", (req, res) -> {
            res.send("Hello, World!");
        });

        app.listen(3000, () -> {
            System.out.println("Server running at http://localhost:3000");
        });
    }
}
```

Run it, then open your browser and go to `http://localhost:3000`. You will see **Hello, World!**

---

## Step 3 — Add JSON support

Most APIs return JSON. Here is how to do that:

```java
import io.github.dhruvrawatdev.expressify.Expressify;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Expressify app = new Expressify();

        // Parse incoming JSON request bodies
        app.use(Expressify.json());

        // Return a JSON response
        app.get("/user", (req, res) -> {
            res.json(Map.of("name", "Alice", "age", 30));
        });

        // Accept a JSON body in a POST request
        app.post("/user", (req, res) -> {
            Map<?,?> body = (Map<?,?>) req.body();
            System.out.println("Received: " + body);
            res.status(201).json(Map.of("created", true));
        });

        app.listen(3000);
    }
}
```

---

## Step 4 — A more complete example

```java
import io.github.dhruvrawatdev.expressify.Expressify;
import io.github.dhruvrawatdev.expressify.middleware.cors.CorsOptions;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        Expressify app = new Expressify();

        // --- Middleware (runs on every request) ---
        app.use(Expressify.json());          // parse JSON bodies
        app.use(Expressify.cors());          // allow cross-origin requests
        app.use(Expressify.morgan("dev"));   // log every request to console

        // --- Routes ---
        app.get("/", (req, res) -> {
            res.send("Welcome to my API!");
        });

        app.get("/users/:id", (req, res) -> {
            String id = req.param("id");
            res.json(Map.of("id", id, "name", "User-" + id));
        });

        app.post("/users", (req, res) -> {
            Map<?,?> body = (Map<?,?>) req.body();
            res.status(201).json(Map.of("created", body));
        });

        // --- Error handler (always register last) ---
        app.error((err, req, res, next) -> {
            res.status(500).json(Map.of("error", err.getMessage()));
        });

        // --- Start server ---
        app.listen(3000, () -> {
            System.out.println("API running at http://localhost:3000");
        });
    }
}
```

---

## How Expressify works — the big picture

When a request comes in, Expressify runs it through a **pipeline** of handlers in order:

```
HTTP Request
    ↓
Global Middleware (app.use(...))     ← runs for every request
    ↓
Path Middleware (app.use('/path'))   ← runs only for matching path
    ↓
Route Handler (app.get('/path'))     ← runs when method + path match
    ↓
Error Handler (app.error(...))       ← runs only if something went wrong
    ↓
HTTP Response
```

Each step can either:
- **Send a response** and stop the pipeline
- **Call `next.run()`** to pass to the next step
- **Call `next.error(err)`** to jump straight to the error handler

---

## Key concepts (quick reference)

| Concept | What it is |
|---|---|
| `Expressify app` | Your application object — like Express's `app` |
| `app.get(path, handler)` | Handle a GET request to a path |
| `app.use(handler)` | Register middleware (runs before routes) |
| `req` | The incoming HTTP request object |
| `res` | The outgoing HTTP response object |
| `next.run()` | Pass control to the next handler in the chain |
| `next.error(err)` | Send an error to the error handler |

---

## Next steps

- [03 — Routing](03_routing.md) — All HTTP methods, path params, wildcards
- [04 — Middleware](04_middleware.md) — How middleware works in depth
- [05 — Request Object](05_request.md) — Everything `req` can do
- [06 — Response Object](06_response.md) — Everything `res` can do
