package io.github.dhruvrawatdev.expressify.middleware.ratelimit_header_parser;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses standard HTTP rate-limit headers into a {@link ParsedRateLimit} value object.
 *
 * <p>Mirrors the {@code ratelimit-header-parser} npm package. Supports:
 * <ul>
 *   <li>IETF draft-7 combined header: {@code RateLimit: limit=N, remaining=N, reset=N}</li>
 *   <li>Draft-6 separate headers: {@code RateLimit-Limit}, {@code RateLimit-Remaining}, {@code RateLimit-Reset}</li>
 *   <li>Legacy {@code X-RateLimit-*} headers (GitHub, GitLab, most REST APIs)</li>
 *   <li>Twitter-style {@code X-Rate-Limit-*} headers</li>
 *   <li>{@code Retry-After} fallback for the reset time</li>
 * </ul>
 *
 * <pre>{@code
 * Map<String, String> headers = Map.of(
 *     "X-RateLimit-Limit",     "100",
 *     "X-RateLimit-Remaining", "42",
 *     "X-RateLimit-Reset",     "1716312000"
 * );
 * ParsedRateLimit info = RateLimitHeaderParser.parse(headers);
 * System.out.println(info.getRemaining()); // 42
 * }</pre>
 */
public final class RateLimitHeaderParser {

    private static final Pattern RE_LIMIT = Pattern.compile("limit\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_REMAINING = Pattern.compile("remaining\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_RESET = Pattern.compile("reset\\s*=\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RE_LETTERS = Pattern.compile("[a-zA-Z]");

    private RateLimitHeaderParser() {}

    /**
     * Parse rate-limit headers with auto-detection of the reset value format.
     *
     * @param headers  case-insensitive map of HTTP response headers
     * @return parsed rate-limit info, or {@code null} if no recognised headers are present
     */
    public static ParsedRateLimit parse(Map<String, String> headers) {
        return parse(headers, RateLimitHeaderParserOptions.builder().build());
    }

    /**
     * Parse rate-limit headers with explicit options.
     *
     * @param headers  case-insensitive map of HTTP response headers
     * @param opts     parsing options (reset type hint)
     * @return parsed rate-limit info, or {@code null} if no recognised headers are present
     */
    public static ParsedRateLimit parse(Map<String, String> headers, RateLimitHeaderParserOptions opts) {
        // IETF draft-7: combined RateLimit header
        String combined = getHeader(headers, "ratelimit");
        if (combined != null) return parseCombined(combined);

        // Determine the prefix used by this API
        String prefix;
        if      (getHeader(headers, "ratelimit-remaining")    != null) prefix = "ratelimit-";
        else if (getHeader(headers, "x-ratelimit-remaining")  != null) prefix = "x-ratelimit-";
        else if (getHeader(headers, "x-rate-limit-remaining") != null) prefix = "x-rate-limit-";
        else return null; // no recognised rate-limit headers

        int limit     = toInt(getHeader(headers, prefix + "limit"));
        int usedA     = toInt(getHeader(headers, prefix + "used"));
        int usedB     = toInt(getHeader(headers, prefix + "observed"));
        int used      = usedA >= 0 ? usedA : usedB; // prefer "used", fall back to "observed"
        int remaining = toInt(getHeader(headers, prefix + "remaining"));

        Instant reset = parseReset(
                getHeader(headers, prefix + "reset"),
                getHeader(headers, "retry-after"),
                opts.getResetType());

        // Handle APIs that omit certain fields (e.g. Reddit omits limit, most omit used)
        int resolvedLimit = (limit < 0) ? used + remaining : limit;
        int resolvedUsed  = (used  < 0) ? (resolvedLimit >= 0 ? resolvedLimit - remaining : -1) : used;

        return new ParsedRateLimit(resolvedLimit, resolvedUsed, remaining, reset);
    }

    // Parsers

    private static ParsedRateLimit parseCombined(String header) {
        Matcher mLimit = RE_LIMIT.matcher(header);
        Matcher mRemaining = RE_REMAINING.matcher(header);
        Matcher mReset = RE_RESET.matcher(header);

        int limit = mLimit.find() ? toInt(mLimit.group(1))     : -1;
        int remaining = mRemaining.find() ? toInt(mRemaining.group(1)) : -1;
        int resetSec  = mReset.find() ? toInt(mReset.group(1))     : -1;

        Instant reset = (resetSec >= 0) ? Instant.now().plusSeconds(resetSec) : null;
        int used = (limit >= 0 && remaining >= 0) ? limit - remaining : -1;

        return new ParsedRateLimit(limit, used, remaining, reset);
    }

    private static Instant parseReset(String resetRaw, String retryAfter,
                                       RateLimitHeaderParserOptions.ResetType type) {
        if (resetRaw == null) {
            return (retryAfter != null) ? parseResetUnix(retryAfter) : null;
        }
        return switch (type) {
            case DATE -> parseResetDate(resetRaw);
            case UNIX -> parseResetUnix(resetRaw);
            case SECONDS -> parseResetSeconds(resetRaw);
            case MILLISECONDS -> parseResetMilliseconds(resetRaw);
            default -> parseResetAuto(resetRaw);
        };
    }

    private static Instant parseResetDate(String raw) {
        try { return Instant.parse(raw); } catch (Exception ignored) {}
        try { return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); } catch (Exception ignored) {}
        return Instant.now();
    }

    private static Instant parseResetUnix(String raw) {
        long secs = toLong(raw);
        return secs > 0 ? Instant.ofEpochSecond(secs) : Instant.now();
    }

    private static Instant parseResetSeconds(String raw) {
        long secs = toLong(raw);
        return Instant.now().plusSeconds(Math.max(0, secs));
    }

    private static Instant parseResetMilliseconds(String raw) {
        long ms = toLong(raw);
        return Instant.now().plusMillis(Math.max(0, ms));
    }

    private static Instant parseResetAuto(String raw) {
        if (RE_LETTERS.matcher(raw).find()) return parseResetDate(raw);
        long n = toLong(raw);
        if (n > 1_000_000_000L) return parseResetUnix(raw); // looks like a Unix timestamp (after ~2001)
        return parseResetSeconds(raw);                        // treat as seconds-from-now
    }

    // Helpers

    private static String getHeader(Map<String, String> headers, String name) {
        String v = headers.get(name);
        if (v != null) return v;
        // Case-insensitive fallback for non-lowercased maps
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (name.equalsIgnoreCase(e.getKey())) return e.getValue();
        }
        return null;
    }

    private static int toInt(String s) {
        if (s == null) return -1;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    private static long toLong(String s) {
        if (s == null) return -1L;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1L; }
    }
}
