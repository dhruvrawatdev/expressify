# 31 — Validator

The validator middleware validates and sanitizes request data (body, query params, route params, headers, cookies). It follows the express-validator pattern.

---

## Basic concept

1. Add validation chains as middleware on your routes
2. Inside the route handler, check if there were any validation errors
3. If errors exist, return 422. If not, proceed.

---

## Setup

No extra imports needed — Validator is part of Expressify.

```java
import io.github.dhruvrawatdev.expressify.middleware.validator.Validator;
import io.github.dhruvrawatdev.expressify.middleware.validator.ValidationResult;
```

---

## Validate body fields

```java
app.use(Expressify.json());  // need this to parse JSON first

app.post("/register",
    Validator.body("email").notEmpty().isEmail(),      // email is required + valid format
    Validator.body("password").notEmpty().isLength(8, 100), // password 8–100 chars
    Validator.body("age").optional().isInt(0, 150),    // age is optional, 0–150 if provided

    (req, res, next) -> {
        ValidationResult result = Validator.validationResult(req);
        if (!result.isEmpty()) {
            res.status(422).json(result.array());
            return;
        }
        // All inputs are valid — proceed
        res.status(201).json(Map.of("registered", true));
    }
);
```

---

## Validate query params

```java
app.get("/search",
    Validator.query("q").notEmpty().withMessage("Search query is required"),
    Validator.query("page").optional().isInt(1, 1000),
    Validator.query("limit").optional().isInt(1, 100),

    (req, res, next) -> {
        ValidationResult result = Validator.validationResult(req);
        if (!result.isEmpty()) {
            res.status(400).json(result.array());
            return;
        }
        res.json(Map.of("results", search(req.query("q"))));
    }
);
```

---

## Validate route params

```java
app.get("/users/:id",
    Validator.param("id").isNumeric().withMessage("User ID must be a number"),

    (req, res, next) -> {
        ValidationResult result = Validator.validationResult(req);
        if (!result.isEmpty()) {
            res.status(400).json(result.array()); return;
        }
        res.json(Map.of("userId", req.param("id")));
    }
);
```

---

## All validator methods

### Validators — check if value is valid

| Method | Description |
|---|---|
| `notEmpty()` | Value is not null, not empty string |
| `isEmail()` | Valid email format |
| `isLength(min, max)` | String length is between min and max |
| `isInt()` | Value is an integer |
| `isInt(min, max)` | Integer in range |
| `isFloat()` | Value is a decimal number |
| `isNumeric()` | Value contains only digits |
| `isAlpha()` | Letters only |
| `isAlphanumeric()` | Letters and digits only |
| `isBoolean()` | "true" or "false" |
| `isURL()` | Valid URL |
| `isUUID()` | Valid UUID format |
| `matches(regex)` | Matches a regular expression |
| `custom((value, req) -> bool)` | Your own validation logic |
| `optional()` | Skip validation if field is missing |

### Sanitizers — transform values

| Method | Description |
|---|---|
| `trim()` | Remove leading/trailing whitespace |
| `toLowerCase()` | Convert to lowercase |
| `toUpperCase()` | Convert to uppercase |
| `toInt()` | Parse as integer |
| `toFloat()` | Parse as float |
| `toBoolean()` | Parse as boolean |
| `customSanitizer(fn)` | Your own transform |

### Messages

| Method | Description |
|---|---|
| `withMessage(String)` | Set error message for the last check |

---

## Custom validation

```java
Validator.body("username")
    .notEmpty()
    .custom((value, req) -> {
        // value is Object, convert to String
        String username = String.valueOf(value);
        // Check it only has letters and numbers
        return username.matches("[a-zA-Z0-9]+");
    })
    .withMessage("Username must only contain letters and numbers")
```

---

## Custom sanitizer

```java
Validator.body("email")
    .trim()
    .toLowerCase()
    .isEmail()
```

---

## Multiple fields validated

```java
app.post("/create-post",
    Validator.body("title").notEmpty().isLength(1, 200),
    Validator.body("content").notEmpty().isLength(1, 10000),
    Validator.body("tags").optional(),
    Validator.body("published").optional().toBoolean(),

    (req, res, next) -> {
        ValidationResult result = Validator.validationResult(req);
        if (!result.isEmpty()) {
            res.status(422).json(Map.of(
                "errors", result.array(),
                "count",  result.size()
            ));
            return;
        }
        res.status(201).json(Map.of("created", true));
    }
);
```

---

## ValidationResult methods

```java
ValidationResult result = Validator.validationResult(req);

result.isEmpty();      // true if no errors
result.size();         // number of errors
result.array();        // List<ValidationError> — all errors
result.first();        // first error, or null
result.mapped();       // Map<field, first error for that field>
```

---

## ValidationError methods

```java
ValidationError err = result.first();

err.field();     // "email"
err.value();     // "not-an-email"
err.message();   // "Invalid email"
err.location();  // "body", "query", "params", etc.
```

---

## Where to validate

```java
Validator.body(field)     // req.body() — JSON or form fields
Validator.query(field)    // req.query() — URL query params
Validator.param(field)    // req.param() — URL path params
Validator.header(field)   // req.get() — request headers
Validator.cookie(field)   // req.cookie() — cookies
```

---

## Stop on first error — `bail()`

```java
// Stop running validators for this field after first failure
Validator.body("email")
    .notEmpty().bail()   // stop here if empty
    .isEmail()           // only runs if not empty
```

---

## Next steps

- [32 — Dev Error Handler](32_dev_error_handler.md)
- [07 — Error Handling](07_error_handling.md)
