# 37 — Complete Application Example

This page builds a small but realistic application that uses many of Expressify's features together: REST API, authentication middleware, file uploads, WebSocket, and error handling.

---

## What we're building

A **note-taking app** with:
- REST API (`GET /notes`, `POST /notes`, `PUT /notes/:id`, `DELETE /notes/:id`)
- Session-based login (`POST /login`, `POST /logout`)
- File attachment upload (`POST /notes/:id/attach`)
- Real-time broadcast (WebSocket) — notify all connected clients when a note changes
- Global error handling using `HttpException` types

---

## Project layout

```
src/main/java/io/github/dhruvrawatdev/myapp/
├── Main.java
├── controllers/
│   ├── AuthController.java
│   └── NoteController.java
├── middlewares/
│   └── AuthMiddleware.java
└── routes/
    ├── AuthRoutes.java
    └── NoteRoutes.java

src/main/resources/
└── public/
    └── index.html   (optional frontend)
```

---

## Main.java — server setup

```java
package io.github.dhruvrawatdev.myapp;

import io.github.dhruvrawatdev.expressify.Expressify;
import io.github.dhruvrawatdev.expressify.router.core.Router;
import io.github.dhruvrawatdev.expressify.exceptions.*;
import io.github.dhruvrawatdev.expressify.middleware.multer.MulterException;
import io.github.dhruvrawatdev.expressify.middleware.session.SessionMiddleware;
import io.github.dhruvrawatdev.expressify.websocket.WsServer;

import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Expressify app = new Expressify();

        // ── Settings ──────────────────────────────────────────────────────
        app.disable("x-powered-by");

        // ── Global middleware ──────────────────────────────────────────────
        app.use(Expressify.json());
        app.use(Expressify.morgan("dev"));
        app.use(Expressify.cors());
        app.use(SessionMiddleware.configure(
            SessionMiddleware.SessionOptions.builder()
                .secret("change-me-in-production")
                .maxAge(3600_000)   // 1 hour
                .build()
        ));

        // ── Static files ───────────────────────────────────────────────────
        app.use(Expressify.staticFiles("src/main/resources/public"));

        // ── WebSocket — real-time updates ──────────────────────────────────
        WsServer notesWs = app.wsServer("/ws/notes");
        notesWs.onConnection((ws, req) -> {
            ws.onOpen(() -> ws.send("{\"type\":\"welcome\"}"));
        });

        // ── HTTP routes ────────────────────────────────────────────────────
        Router authRouter  = AuthRoutes.create();
        Router noteRouter  = NoteRoutes.create(notesWs);

        app.use("/auth",  authRouter);
        app.use("/notes", noteRouter);

        // ── 404 fallback ───────────────────────────────────────────────────
        app.use((req, res, next) -> {
            throw new NotFoundException("Route not found: " + req.path());
        });

        // ── Global error handler (MUST be last) ────────────────────────────
        app.error((err, req, res, next) -> {
            if (err instanceof HttpException http) {
                res.status(http.getStatusCode()).json(Map.of(
                    "error",  http.getMessage(),
                    "status", http.getStatusCode()
                ));
            } else if (err instanceof MulterException me) {
                res.status(400).json(Map.of(
                    "error", "Upload failed",
                    "code",  me.getCode()
                ));
            } else {
                System.err.println("Unhandled: " + err.getMessage());
                res.status(500).json(Map.of("error", "Internal Server Error"));
            }
        });

        app.listen(3000, () -> System.out.println("Notes app ready on http://localhost:3000"));
    }
}
```

---

## AuthMiddleware.java

```java
package io.github.dhruvrawatdev.myapp.middlewares;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.exceptions.UnauthorizedException;

public class AuthMiddleware {

    public static final RouteHandler requireLogin = (req, res, next) -> {
        String userId = req.session("userId");
        if (userId == null) throw new UnauthorizedException("Please log in");
        req.locals().put("userId", userId);
        next.run();
    };
}
```

---

## AuthRoutes.java

```java
package io.github.dhruvrawatdev.myapp.routes;

import io.github.dhruvrawatdev.expressify.router.core.Router;
import io.github.dhruvrawatdev.expressify.exceptions.UnauthorizedException;
import io.github.dhruvrawatdev.myapp.controllers.AuthController;

import java.util.Map;

public class AuthRoutes {

    public static Router create() {
        Router router = new Router();
        AuthController ctrl = new AuthController();

        // POST /auth/login
        router.post("/login", (req, res) -> {
            Map<?, ?> body = req.body();
            String username = (String) body.get("username");
            String password = (String) body.get("password");

            if (username == null || password == null) {
                throw new io.github.dhruvrawatdev.expressify.exceptions.BadRequestException(
                    "username and password required");
            }

            String userId = ctrl.authenticate(username, password);
            if (userId == null) throw new UnauthorizedException("Invalid credentials");

            req.setSession("userId", userId);
            res.json(Map.of("message", "Logged in", "userId", userId));
        });

        // POST /auth/logout
        router.post("/logout", (req, res) -> {
            req.clearSession();
            res.json(Map.of("message", "Logged out"));
        });

        return router;
    }
}
```

---

## NoteRoutes.java

```java
package io.github.dhruvrawatdev.myapp.routes;

import io.github.dhruvrawatdev.expressify.router.core.Router;
import io.github.dhruvrawatdev.expressify.middleware.multer.Multer;
import io.github.dhruvrawatdev.expressify.middleware.multer.MultipartOptions;
import io.github.dhruvrawatdev.expressify.exceptions.NotFoundException;
import io.github.dhruvrawatdev.expressify.websocket.WsServer;
import io.github.dhruvrawatdev.myapp.controllers.NoteController;
import io.github.dhruvrawatdev.myapp.middlewares.AuthMiddleware;

import java.util.Map;

public class NoteRoutes {

    public static Router create(WsServer notesWs) {
        Router router = new Router();
        NoteController ctrl = new NoteController();

        // File upload middleware (5 MB limit)
        var upload = Multer.create(MultipartOptions.builder()
            .maxSize(5 * 1024 * 1024)
            .allowedFields("attachment")
            .build());

        // All note routes require login
        router.use(AuthMiddleware.requireLogin);

        // GET /notes — list all notes
        router.get("/", (req, res) -> {
            String userId = (String) req.locals().get("userId");
            res.json(ctrl.listNotes(userId));
        });

        // POST /notes — create a note
        router.post("/", (req, res) -> {
            Map<?, ?> body = req.body();
            String userId = (String) req.locals().get("userId");

            if (body == null || body.get("text") == null) {
                throw new io.github.dhruvrawatdev.expressify.exceptions.BadRequestException(
                    "text is required");
            }

            Map<String, Object> note = ctrl.createNote(userId, (String) body.get("text"));
            notesWs.broadcast("{\"type\":\"created\",\"noteId\":\"" + note.get("id") + "\"}");
            res.status(201).json(note);
        });

        // PUT /notes/:id — update a note
        router.put("/:id", (req, res) -> {
            String userId = (String) req.locals().get("userId");
            String id     = req.param("id");
            Map<?, ?> body = req.body();

            Map<String, Object> updated = ctrl.updateNote(userId, id, (String) body.get("text"));
            if (updated == null) throw new NotFoundException("Note not found");

            notesWs.broadcast("{\"type\":\"updated\",\"noteId\":\"" + id + "\"}");
            res.json(updated);
        });

        // DELETE /notes/:id — delete a note
        router.delete("/:id", (req, res) -> {
            String userId = (String) req.locals().get("userId");
            String id     = req.param("id");

            boolean deleted = ctrl.deleteNote(userId, id);
            if (!deleted) throw new NotFoundException("Note not found");

            notesWs.broadcast("{\"type\":\"deleted\",\"noteId\":\"" + id + "\"}");
            res.status(204).end();
        });

        // POST /notes/:id/attach — upload a file attachment
        router.post("/:id/attach", upload.single("attachment"), (req, res) -> {
            var file = req.file("attachment");
            if (file == null) {
                throw new io.github.dhruvrawatdev.expressify.exceptions.BadRequestException(
                    "attachment file is required");
            }

            String noteId = req.param("id");
            res.json(Map.of(
                "noteId",   noteId,
                "filename", file.originalFilename(),
                "size",     file.size(),
                "mimetype", file.mimetype()
            ));
        });

        return router;
    }
}
```

---

## Connecting from the browser

```js
// Connect WebSocket for real-time updates
const ws = new WebSocket('ws://localhost:3000/ws/notes');

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('Update:', data.type, data.noteId);

    if (data.type === 'created' || data.type === 'updated') {
        fetchNotes();  // reload list
    }
    if (data.type === 'deleted') {
        removeNoteFromUI(data.noteId);
    }
};

// Login
async function login(username, password) {
    const res = await fetch('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
        credentials: 'include'   // send session cookie
    });
    return res.json();
}

// Get all notes
async function fetchNotes() {
    const res = await fetch('/notes', { credentials: 'include' });
    const notes = await res.json();
    renderNotes(notes);
}

// Create a note
async function createNote(text) {
    const res = await fetch('/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
        credentials: 'include'
    });
    return res.json();
}

// Upload a file attachment
async function attachFile(noteId, file) {
    const form = new FormData();
    form.append('attachment', file);
    const res = await fetch('/notes/' + noteId + '/attach', {
        method: 'POST',
        body: form,
        credentials: 'include'
    });
    return res.json();
}
```

---

## Testing with curl

```bash
# Login
curl -c cookies.txt -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret"}'

# Create a note
curl -b cookies.txt -X POST http://localhost:3000/notes \
  -H "Content-Type: application/json" \
  -d '{"text":"Buy groceries"}'

# List notes
curl -b cookies.txt http://localhost:3000/notes

# Update a note
curl -b cookies.txt -X PUT http://localhost:3000/notes/1 \
  -H "Content-Type: application/json" \
  -d '{"text":"Buy groceries and milk"}'

# Upload attachment
curl -b cookies.txt -X POST http://localhost:3000/notes/1/attach \
  -F "attachment=@/path/to/file.pdf"

# Delete a note
curl -b cookies.txt -X DELETE http://localhost:3000/notes/1

# Logout
curl -b cookies.txt -X POST http://localhost:3000/auth/logout
```

---

## Key patterns used in this example

| Pattern | Where |
|---|---|
| Sub-router mounted at `/auth` and `/notes` | `Main.java` |
| Global middleware applied to all notes routes via `router.use()` | `NoteRoutes.java` |
| `HttpException` subclasses thrown from handlers | Throughout |
| `MulterException` caught in the error handler | `Main.java` |
| `req.locals()` to pass data between middleware and route | `AuthMiddleware`, `NoteRoutes` |
| `WsServer.broadcast()` to push updates to all clients | `NoteRoutes.java` |
| `req.session()` / `req.setSession()` for login state | `AuthRoutes.java` |
| 404 fallback middleware registered before the error handler | `Main.java` |
| Error handler registered last | `Main.java` |

---

## What to read next

Now that you've seen the whole framework in action, revisit the individual doc pages for the features you want to go deeper on:

- [03 — Routing](03_routing.md) — path params, wildcards, `app.route()`
- [04 — Middleware](04_middleware.md) — how middleware chains work
- [07 — Error Handling](07_error_handling.md) — error propagation in depth
- [10 — Async Handlers](10_async_handlers.md) — `getAsync`, `CompletableFuture`
- [17 — Session](17_session.md) — session options, custom stores
- [22 — Multer](22_multer.md) — file upload configuration
- [33 — WebSocket](33_websocket.md) — broadcast, heartbeat, options
- [34 — Socket.IO](34_socketio.md) — rooms, namespaces, acknowledgements
- [35 — Exceptions](35_exceptions.md) — all built-in exception types
