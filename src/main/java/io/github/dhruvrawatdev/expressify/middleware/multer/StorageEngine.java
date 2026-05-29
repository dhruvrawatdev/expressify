package io.github.dhruvrawatdev.expressify.middleware.multer;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.io.InputStream;

/**
 * Strategy for persisting uploaded files.
 * Implement this interface to create custom storage backends.
 */
public interface StorageEngine {
    /**
     * Persist the file data and return populated metadata.
     *
     * @param req          current request (useful for dynamic destination/filename callbacks)
     * @param fieldName    form field name
     * @param originalName original filename from the browser
     * @param mimetype     detected content type
     * @param encoding     transfer encoding (usually "7bit")
     * @param size         file size in bytes (-1 if unknown)
     * @param stream       file data stream (will be drained by this method)
     */
    UploadedFile store(Request req, String fieldName, String originalName,
                       String mimetype, String encoding, long size,
                       InputStream stream) throws Exception;
}
