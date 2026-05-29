# 09 — Template Engines

A template engine lets you create HTML pages with dynamic data. Instead of building HTML strings in Java code, you write template files and pass data to them.

Expressify supports **6 template engines**. Pick the one you like — they all work the same way.

---

## Quick start

1. Create a template file in `src/main/resources/templates/`
2. Call `res.render("template-name", model)` in your route

```java
app.set("view engine", "pebble");  // choose your engine

app.get("/profile", (req, res) -> {
    res.render("profile", Map.of(
        "name", "Alice",
        "email", "alice@example.com"
    ));
});
```

---

## Available engines

| Engine | File extension | Style |
|---|---|---|
| `thymeleaf` (default) | `.html` | HTML-like attributes |
| `pebble` | `.html` | Jinja2 / Django style |
| `freemarker` | `.ftl` | FTL syntax |
| `jte` | `.jte` | Java-like templates |
| `handlebars` | `.hbs` | Mustache `{{variable}}` style |
| `velocity` | `.vm` | `$variable` style |

---

## Switching the engine

```java
app.set("view engine", "pebble");      // or:
app.set("view engine", "freemarker");
app.set("view engine", "thymeleaf");
app.set("view engine", "jte");
app.set("view engine", "handlebars");
app.set("view engine", "velocity");
```

---

## Template directory

All templates live in:

```
src/main/resources/templates/
```

Change it with:

```java
app.set("views", "src/main/resources/views");
```

---

## `res.render()` — the rendering API

### Render with no data

```java
res.render("home");
// looks for: templates/home.html (or .pebble, .ftl, etc.)
```

### Render with data (model)

```java
res.render("profile", Map.of(
    "username", "alice",
    "role", "admin",
    "items", List.of("a", "b", "c")
));
```

### Render with a specific engine (override)

```java
res.render("report", model, "freemarker");  // use FreeMarker even if default is Pebble
```

---

## Thymeleaf (default)

File: `templates/profile.html`

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title th:text="${title}">Profile</title>
</head>
<body>
    <h1>Hello, <span th:text="${username}">User</span>!</h1>

    <p>Role: <span th:text="${role}">user</span></p>

    <ul>
        <li th:each="item : ${items}" th:text="${item}">item</li>
    </ul>

    <div th:if="${showExtra}">
        <p>Extra content</p>
    </div>
</body>
</html>
```

Route:
```java
app.set("view engine", "thymeleaf");
app.get("/profile", (req, res) -> {
    res.render("profile", Map.of(
        "title", "My Profile",
        "username", "alice",
        "role", "admin",
        "items", List.of("Java", "Expressify", "SQL"),
        "showExtra", true
    ));
});
```

---

## Pebble

File: `templates/profile.html`

```html
<!DOCTYPE html>
<html>
<head><title>{{ title }}</title></head>
<body>
    <h1>Hello, {{ username }}!</h1>
    <p>Role: {{ role }}</p>

    <ul>
        {% for item in items %}
            <li>{{ item }}</li>
        {% endfor %}
    </ul>

    {% if showExtra %}
        <p>Extra content</p>
    {% endif %}
</body>
</html>
```

Route:
```java
app.set("view engine", "pebble");
app.get("/profile", (req, res) -> {
    res.render("profile", Map.of(
        "title", "My Profile",
        "username", "alice",
        "role", "admin",
        "items", List.of("Java", "Expressify", "SQL"),
        "showExtra", true
    ));
});
```

---

## FreeMarker

File: `templates/profile.ftl`

```freemarker
<!DOCTYPE html>
<html>
<head><title>${title}</title></head>
<body>
    <h1>Hello, ${username}!</h1>
    <p>Role: ${role}</p>

    <ul>
        <#list items as item>
            <li>${item}</li>
        </#list>
    </ul>

    <#if showExtra>
        <p>Extra content</p>
    </#if>
</body>
</html>
```

Route:
```java
app.set("view engine", "freemarker");
app.get("/profile", (req, res) -> {
    res.render("profile", Map.of(
        "title", "My Profile",
        "username", "alice",
        "items", List.of("Java", "Expressify")
    ));
});
```

---

## Handlebars

File: `templates/profile.hbs`

```handlebars
<!DOCTYPE html>
<html>
<head><title>{{title}}</title></head>
<body>
    <h1>Hello, {{username}}!</h1>
    <p>Role: {{role}}</p>

    <ul>
        {{#each items}}
            <li>{{this}}</li>
        {{/each}}
    </ul>

    {{#if showExtra}}
        <p>Extra content</p>
    {{/if}}
</body>
</html>
```

---

## Velocity

File: `templates/profile.vm`

```velocity
<!DOCTYPE html>
<html>
<head><title>$title</title></head>
<body>
    <h1>Hello, $username!</h1>
    <p>Role: $role</p>

    <ul>
        #foreach($item in $items)
            <li>$item</li>
        #end
    </ul>

    #if($showExtra)
        <p>Extra content</p>
    #end
</body>
</html>
```

---

## Custom engine

You can register your own template engine:

```java
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;

app.engine("mustache", (templateName, model, viewsDir) -> {
    // Load the template file
    // Fill in model data
    // Return rendered HTML string
    return renderedHtml;
});

app.set("view engine", "mustache");
```

---

## App.locals in templates

Values in `app.locals` are automatically available in all templates:

```java
app.locals.put("appName", "My App");
app.locals.put("year", 2024);
```

Pebble template:
```html
<footer>{{ appName }} &copy; {{ year }}</footer>
```

Thymeleaf template:
```html
<footer th:text="${appName + ' © ' + year}"></footer>
```

---

## Organizing templates into folders

```java
// For templates in a subfolder
res.render("auth/login");      // → templates/auth/login.html
res.render("emails/welcome");  // → templates/emails/welcome.html
```

---

## Next steps

- [05 — Request Object](05_request.md)
- [06 — Response Object](06_response.md)
- [08 — App Settings](08_settings.md)
