package io.github.dhruvrawatdev.expressify.middleware.multer;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.io.InputStream;

/**
 * In-memory storage engine for {@link Multer}.
 * File bytes are held in {@link UploadedFile#buffer()} (no disk I/O).
 */
public class MemoryStorage implements StorageEngine {

    public MemoryStorage(MultipartOptions opts) {}

    @Override
    public UploadedFile store(Request req, String fieldName, String originalName,
                              String mimetype, String encoding, long size,
                              InputStream stream) throws Exception {
        byte[] data = stream.readAllBytes();
        return UploadedFile.builder()
                .fieldName(fieldName)
                .originalName(originalName)
                .mimetype(mimetype)
                .encoding(encoding)
                .size(data.length)
                .buffer(data)
                .build();
    }
}
