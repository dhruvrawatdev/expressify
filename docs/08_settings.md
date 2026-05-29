# 08 — App Settings

Expressify has a settings system that lets you configure the framework's behavior. It works like Express.js's `app.set()` / `app.get()`.

---

## Setting a value — `app.set(key, value)`

```java
app.set("view engine", "pebble");         // change template engine
app.set("views", "src/main/resources/templates"); // template directory
app.set("x-powered-by", false);           // disable X-Powered-By header
app.set("etag", true);                    // enable ETag headers
app.set("myCustomSetting", "hello");      // any key you want
```

---

## Reading a value — `app.get(key)`

```java
Object engine = app.get("view engine");   // "pebble"
boolean etag  = (boolean) app.get("etag"); // true
String custom = (String) app.get("myCustomSetting"); // "hello"
```

---

## Enable / Disable boolean settings

```java
app.enable("etag");           // same as app.set("etag", true)
app.disable("x-powered-by"); // same as app.set("x-powered-by", false)

app.enabled("etag");          // returns true
app.disabled("x-powered-by"); // returns true
```

---

## Built-in settings

| Setting key | Default | What it does |
|---|---|---|
| `"view engine"` | `"thymeleaf"` | Which template engine to use for `res.render()` |
| `"views"` | `"src/main/resources/templates"` | Where template files are stored |
| `"x-powered-by"` | `true` | Whether to send `X-Powered-By: Expressify` header |
| `"etag"` | `true` | Whether to send `ETag` headers on responses |

---

## Template engine setting

```java
// Use Pebble templates
app.set("view engine", "pebble");

// Use FreeMarker templates
app.set("view engine", "freemarker");

// Use Thymeleaf (default)
app.set("view engine", "thymeleaf");

// Available: thymeleaf, pebble, freemarker, jte, handlebars, velocity
```

---

## Custom template directory

```java
app.set("views", "src/main/resources/views");  // default is "templates"
```

---

## Remove X-Powered-By header

By default, every response includes `X-Powered-By: Expressify`. Remove it for security:

```java
app.disable("x-powered-by");
// or
app.set("x-powered-by", false);
```

---

## Register a custom template engine

```java
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;

app.engine("mustache", new MyMustacheEngine());
app.set("view engine", "mustache");

// Now res.render("home") will look for home.mustache
```

---

## `app.locals` — application-wide data

`app.locals` is a `Map<String, Object>` that is available everywhere, including in templates.

```java
// Set application-wide data
app.locals.put("appName", "My App");
app.locals.put("version", "2.0.0");
app.locals.put("year", 2024);

// Read it anywhere
String name = (String) app.locals.get("appName");
```

In templates, `app.locals` values are automatically available:

```html
<!-- Thymeleaf example -->
<p th:text="${appName}">App Name</p>
<p th:text="${version}">Version</p>
```

---

## Example — complete settings setup

```java
Expressify app = new Expressify();

// Settings
app.set("view engine", "pebble");
app.set("views", "src/main/resources/views");
app.disable("x-powered-by");

// App-wide data for templates
app.locals.put("siteName", "My Blog");
app.locals.put("supportEmail", "help@myblog.com");

// Middleware
app.use(Expressify.json());

// Routes
app.get("/", (req, res) -> {
    res.render("home", Map.of("title", "Home Page"));
});

app.listen(3000);
```

---

## Next steps

- [09 — Template Engines](09_template_engines.md)
- [03 — Routing](03_routing.md)
