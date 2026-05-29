package io.github.dhruvrawatdev.expressify.middleware.vhost;

import java.util.Collections;
import java.util.List;

/**
 * Virtual-host match result stored in {@code req.locals().get("vhost")} by {@link VHost} middleware.
 *
 * <p>Mirrors the Express.js {@code req.vhost} object: provides the matched hostname
 * and any wildcard captures indexed by position.
 *
 * <pre>{@code
 * // Pattern: "*.example.com" matched against "api.example.com"
 * VHostInfo vhost = (VHostInfo) req.locals().get("vhost");
 * String sub      = vhost.get(0);      // "api"
 * String hostname = vhost.hostname();  // "api.example.com"
 * int    count    = vhost.length();    // 1
 * }</pre>
 */
public final class VHostInfo {

    private final String hostname;
    private final List<String> captures;

    VHostInfo(String hostname, List<String> captures) {
        this.hostname = hostname;
        this.captures = Collections.unmodifiableList(captures);
    }

    /**
     * The full hostname from the request (e.g. {@code "api.example.com"}).
     *
     * @return lowercase hostname without port
     */
    public String hostname() { return hostname; }

    /**
     * Number of wildcard captures in the hostname pattern.
     * For pattern {@code "*.example.com"} this is {@code 1};
     * for {@code "*.*.example.com"} this is {@code 2}.
     *
     * @return number of captures
     */
    public int length() { return captures.size(); }

    /**
     * Return the capture group at zero-based {@code index}.
     *
     * <p>For pattern {@code "*.example.com"} matched against {@code "api.example.com"},
     * {@code get(0)} returns {@code "api"}.
     *
     * @param index zero-based index of the wildcard capture
     * @return the captured string segment
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public String get(int index) { return captures.get(index); }

    /**
     * All wildcard captures as an unmodifiable ordered list.
     *
     * @return captures in left-to-right pattern order
     */
    public List<String> captures() { return captures; }

    @Override
    public String toString() {
        return "VHostInfo{hostname='" + hostname + "', captures=" + captures + "}";
    }
}
