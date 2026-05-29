package io.github.dhruvrawatdev.expressify.middleware.multer;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Disk-based storage engine for {@link Multer}.
 * Saves uploaded files to a directory on disk.
 */
public class DiskStorage implements StorageEngine {

    private final MultipartOptions opts;
    private final String defaultDir;

    public DiskStorage(MultipartOptions opts) {
        this.opts = opts;
        this.defaultDir = System.getProperty("java.io.tmpdir");
    }

    @Override
    public UploadedFile store(Request req, String fieldName, String originalName,
                              String mimetype, String encoding, long size,
                              InputStream stream) throws Exception {

        UploadedFile stub = UploadedFile.builder()
                .fieldName(fieldName).originalName(originalName)
                .mimetype(mimetype).encoding(encoding).size(size)
                .build();

        String dir = opts.getDestination() != null
                ? opts.getDestination().destination(req, stub)
                : defaultDir;

        String fname = opts.getFilename() != null
                ? opts.getFilename().filename(req, stub)
                : UUID.randomUUID().toString().replace("-", "");

        Path dest = Paths.get(dir, fname);
        Files.createDirectories(dest.getParent());

        byte[] data = stream.readAllBytes();
        Files.write(dest, data);

        return UploadedFile.builder()
                .fieldName(fieldName)
                .originalName(originalName)
                .mimetype(mimetype)
                .encoding(encoding)
                .size(data.length)
                .destination(dir)
                .filename(fname)
                .path(dest.toAbsolutePath().toString())
                .build();
    }
}
