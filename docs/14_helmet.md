# 14 — Helmet

Helmet adds **security headers** to every response. These headers protect your users from common attacks like clickjacking, XSS, and MIME type sniffing.

One line of code makes your app significantly more secure:

```java
app.use(Expressify.helmet());
```

---

## What headers does Helmet set?

| Header | Default value | Protects against |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | MIME type sniffing |
| `X-Frame-Options` | `SAMEORIGIN` | Clickjacking |
| `Strict-Transport-Security` | `max-age=15552000` | SSL stripping |
| `X-DNS-Prefetch-Control` | `off` | DNS prefetch privacy |
| `X-Download-Options` | `noopen` | IE file execution |
| `X-Permitted-Cross-Domain-Policies` | `none` | Flash/PDF cross-domain |
| `Referrer-Policy` | `no-referrer` | Referrer privacy |
| `Content-Security-Policy` | (set) | XSS, code injection |

Helmet also **removes** the `X-Powered-By` header so attackers can't fingerprint your server.

---

## Basic usage

```java
app.use(Expressify.helmet());
```

This is all you need for most apps.

---

## Customize options

```java
import io.github.dhruvrawatdev.expressify.middleware.helmet.HelmetOptions;

app.use(Expressify.helmet(
    HelmetOptions.builder()
        .referrerPolicy("strict-origin")        // change referrer policy
        .frameguard("DENY")                     // block all framing (default: SAMEORIGIN)
        .hsts(true)                             // enable HSTS
        .hstsMaxAge(31536000)                   // 1 year HSTS
        .hstsIncludeSubDomains(true)            // include subdomains in HSTS
        .dnsPrefetchControl(true)               // allow DNS prefetch
        .dnsPrefetchControlAllow(true)          // set X-DNS-Prefetch-Control: on
        .contentSecurityPolicy(true)            // enable CSP header
        .hidePoweredBy(true)                    // hide X-Powered-By (default: true)
        .build()
));
```

---

## Content Security Policy (CSP)

CSP controls which scripts, styles, and other resources the browser loads. Use `cspDirectives` for fine-grained control:

```java
import java.util.List;
import java.util.Map;

app.use(Expressify.helmet(
    HelmetOptions.builder()
        .contentSecurityPolicy(true)
        .cspDirectives(Map.of(
            "default-src",  List.of("'self'"),
            "script-src",   List.of("'self'", "https://cdn.example.com"),
            "style-src",    List.of("'self'", "'unsafe-inline'"),
            "img-src",      List.of("'self'", "data:", "https:")
        ))
        .build()
));
```

---

## Disable the X-Powered-By header

By default, Helmet removes `X-Powered-By` so hackers can't see what server software you're running.

To keep it (not recommended):

```java
app.use(Expressify.helmet(
    HelmetOptions.builder()
        .hidePoweredBy(false)  // keep the header
        .build()
));
```

---

## Disable specific protections

Each protection can be turned off individually:

```java
app.use(Expressify.helmet(
    HelmetOptions.builder()
        .contentSecurityPolicy(false)  // disable CSP (useful during development)
        .hsts(false)                   // disable HSTS (for HTTP-only servers)
        .build()
));
```

---

## Frameguard — clickjacking protection

Prevents your site from being embedded in iframes on other websites.

```java
.frameguard("DENY")        // block all framing
.frameguard("SAMEORIGIN")  // allow framing from same domain (default)
```

---

## HSTS — force HTTPS

Tells browsers to always use HTTPS for your domain:

```java
HelmetOptions.builder()
    .hsts(true)
    .hstsMaxAge(31536000)           // 1 year
    .hstsIncludeSubDomains(true)    // also applies to subdomains
    .hstsPreload(true)              // submit to browser preload list
    .build()
```

Only use this if your server is always on HTTPS.

---

## Full production example

```java
app.use(Expressify.helmet(
    HelmetOptions.builder()
        .hidePoweredBy(true)
        .frameguard("SAMEORIGIN")
        .hsts(true)
        .hstsMaxAge(31536000)
        .hstsIncludeSubDomains(true)
        .referrerPolicy("strict-origin-when-cross-origin")
        .dnsPrefetchControl(false)
        .contentSecurityPolicy(true)
        .cspDirectives(Map.of(
            "default-src", List.of("'self'"),
            "script-src",  List.of("'self'"),
            "style-src",   List.of("'self'", "'unsafe-inline'")
        ))
        .build()
));
```

---

## Next steps

- [13 — CORS](13_cors.md)
- [15 — Morgan Logger](15_morgan.md)
