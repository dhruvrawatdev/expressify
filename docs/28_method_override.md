# 28 — Method Override

HTML forms can only send `GET` and `POST` requests — not `PUT`, `PATCH`, or `DELETE`. Method Override lets you fake these methods from HTML forms.

---

## The problem

```html
<!-- This doesn't work in HTML — DELETE is not allowed -->
<form method="DELETE" action="/users/42">
    <button>Delete User</button>
</form>
```

---

## The solution — Method Override

```java
import io.github.dhruvrawatdev.expressify.middleware.method_override.MethodOverride;

app.use(MethodOverride.create());
```

Now in your HTML form, use a `POST` with a hidden field:

```html
<form method="POST" action="/users/42">
    <input type="hidden" name="_method" value="DELETE">
    <button>Delete User</button>
</form>
```

Or use the `X-HTTP-Method-Override` header (common for AJAX):

```js
fetch('/users/42', {
    method: 'POST',
    headers: { 'X-HTTP-Method-Override': 'DELETE' }
});
```

The server sees it as a `DELETE` request.

---

## How it works

1. Client sends `POST /users/42` with header `X-HTTP-Method-Override: DELETE`
2. MethodOverride middleware reads the header
3. It changes `req.method()` to return `"DELETE"`
4. Your `app.delete("/users/:id", ...)` route handler runs

---

## Via header (default)

```java
// Reads X-HTTP-Method-Override header
app.use(MethodOverride.create());
```

Supported for: `PUT`, `PATCH`, `DELETE`.

---

## Via query string

```java
import io.github.dhruvrawatdev.expressify.middleware.method_override.MethodOverrideOptions;

// Reads ?_method=PUT from URL
app.use(MethodOverride.create("_method", MethodOverrideOptions.defaults()));
```

HTML:
```html
<form method="POST" action="/users/42?_method=DELETE">
    <button>Delete</button>
</form>
```

---

## Custom getter name

```java
// Use a custom header name
app.use(MethodOverride.create("X-Custom-Method", MethodOverrideOptions.defaults()));
```

---

## Full example with forms

```java
app.use(Expressify.urlencoded());  // parse form bodies
app.use(MethodOverride.create());  // enable method override

app.get("/users/:id/edit", (req, res) -> {
    res.render("user-form", Map.of("userId", req.param("id")));
});

app.put("/users/:id", (req, res) -> {
    // Handles PUT from the form
    String name = req.body("name");
    res.send("Updated user " + req.param("id") + " to " + name);
});

app.delete("/users/:id", (req, res) -> {
    // Handles DELETE from the form
    res.send("Deleted user " + req.param("id"));
});
```

Template (`user-form.html`):
```html
<form method="POST" action="/users/42">
    <input type="hidden" name="_method" value="PUT">
    <input name="name" value="Alice">
    <button>Update</button>
</form>

<form method="POST" action="/users/42">
    <input type="hidden" name="_method" value="DELETE">
    <button>Delete</button>
</form>
```

---

## Next steps

- [29 — VHost](29_vhost.md)
- [04 — Middleware](04_middleware.md)
