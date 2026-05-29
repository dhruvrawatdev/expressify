package io.github.dhruvrawatdev.expressify.middleware.bodyparser;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;

/** Callback to inspect / reject a body before it is committed to the request. */
@FunctionalInterface
public interface BodyVerifier {
    void verify(Request req, Response res, byte[] body, String encoding) throws Exception;
}
