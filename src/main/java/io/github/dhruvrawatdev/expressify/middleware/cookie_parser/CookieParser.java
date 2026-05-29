package io.github.dhruvrawatdev.expressify.middleware.cookie_parser;

import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.github.dhruvrawatdev.expressify.http.Response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cookie parser middleware — port of the Node.js {@code cookie-parser} package.
 *
 * <p>Populates {@code req.cookies()} from the Cookie header (always) and
 * {@code req.signedCookies()} from HMAC-SHA256 verified signed cookies (when
 * a secret is provided). Also stores the first secret on {@code req.secret()}
 * so that {@code res.cookie(name, value, opts.signed(true))} can use it.
 *
 * <pre>{@code
 * // Plain cookies only
 * app.use(CookieParser.create());
 *
 * // With signing secret
 * app.use(CookieParser.create("my-secret"));
 *
 * // Multiple secrets for rotation
 * app.use(CookieParser.create(List.of("new-secret", "old-secret")));
 * }</pre>
 */
public class CookieParser {

    private CookieParser() {}

    /**
     * Parse cookies with no signing support.
     *
     * <p>Populates {@code req.cookies()} from the {@code Cookie} request header.
     * No {@code req.signedCookies()} map is populated.
     *
     * @return a {@link RouteHandler} that parses the {@code Cookie} header into {@code req.cookies()}
     */
    public static RouteHandler create() {
        return create(Collections.emptyList());
    }

    /**
     * Parse and HMAC-verify signed cookies with a single secret.
     *
     * <p>Cookies whose value starts with {@code s:} are verified and, if valid, placed in
     * {@code req.signedCookies()} with the prefix stripped. Invalid signatures are silently
     * dropped (not placed in either map).
     *
     * <pre>{@code
     * app.use(CookieParser.create("my-super-secret"));
     *
     * app.get("/profile", (req, res) -> {
     *     String userId = req.signedCookies().get("userId");
     * });
     * }</pre>
     *
     * @param secret HMAC-SHA256 signing secret used to verify cookie signatures
     * @return a {@link RouteHandler} that parses cookies and verifies signed ones
     */
    public static RouteHandler create(String secret) {
        return create(secret == null || secret.isBlank()
                ? Collections.emptyList()
                : List.of(secret));
    }

    /**
     * Parse and verify signed cookies with multiple secrets — supports secret rotation.
     *
     * <p>The first secret in the list is stored on {@code req.secret()} and used by
     * {@code res.cookie(name, value, CookieOptions.signed(true))} to sign new cookies.
     * All secrets are tried when verifying incoming signed cookies.
     *
     * <pre>{@code
     * // Rotate from "old-secret" to "new-secret" without invalidating existing sessions
     * app.use(CookieParser.create(List.of("new-secret", "old-secret")));
     * }</pre>
     *
     * @param secrets ordered list of signing secrets; index 0 is the active signing key,
     *                the rest are accepted for verification only
     * @return a {@link RouteHandler} that parses cookies and verifies signed ones against all secrets
     */
    public static RouteHandler create(List<String> secrets) {
        return (req, res, next) -> {
            if (!secrets.isEmpty()) {
                req.setSecret(secrets.get(0));
            }

            if (!secrets.isEmpty()) {
                Map<String, String> plain       = new LinkedHashMap<>(req.cookies());
                Map<String, String> signedResult = new LinkedHashMap<>();

                plain.forEach((name, value) -> {
                    if (value != null && value.startsWith("s:")) {
                        String unsigned = unsign(value.substring(2), secrets);
                        if (unsigned != null) {
                            signedResult.put(name, unsigned);
                        }
                    }
                });

                req.setSignedCookies(signedResult);
            }

            next.run();
        };
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Verify a signed cookie value ({@code value.hmac}) against the secret list.
     * Returns the original value on success, or {@code null} if verification fails.
     */
    private static String unsign(String signedValue, List<String> secrets) {
        int dot = signedValue.lastIndexOf('.');
        if (dot < 0) return null;
        String value = signedValue.substring(0, dot);
        String givenMac = signedValue.substring(dot + 1);

        for (String secret : secrets) {
            try {
                String expected = Response.hmacSign(value, secret);
                int expectedDot = expected.lastIndexOf('.');
                if (expectedDot < 0) continue;
                String expectedMac = expected.substring(expectedDot + 1);
                if (constantTimeEquals(givenMac, expectedMac)) {
                    return value;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
