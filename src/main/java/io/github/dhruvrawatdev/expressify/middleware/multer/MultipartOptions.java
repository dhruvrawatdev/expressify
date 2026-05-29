package io.github.dhruvrawatdev.expressify.middleware.multer;

import io.github.dhruvrawatdev.expressify.http.Request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Options for {@link Multer} — mirrors the multer npm package constructor options.
 */
public class MultipartOptions {

    @FunctionalInterface
    public interface FileFilter {
        void check(Request req, UploadedFile file, FilterCallback cb);
    }

    public interface FilterCallback {
        void accept(boolean value);
        void reject(MulterException error);
    }

    @FunctionalInterface
    public interface DestinationCallback {
        String destination(Request req, UploadedFile file);
    }

    @FunctionalInterface
    public interface FilenameCallback {
        String filename(Request req, UploadedFile file);
    }

    private final long maxSize;
    private final List<String> allowedTypes;
    private final FileFilter fileFilter;
    private final DestinationCallback destination;
    private final FilenameCallback filename;
    private final boolean preservePath;

    private MultipartOptions(Builder b) {
        this.maxSize = b.maxSize;
        this.allowedTypes = Collections.unmodifiableList(b.allowedTypes);
        this.fileFilter = b.fileFilter;
        this.destination = b.destination;
        this.filename = b.filename;
        this.preservePath = b.preservePath;
    }

    public long getMaxSize() { return maxSize; }
    public List<String> getAllowedTypes() { return allowedTypes; }
    public FileFilter getFileFilter() { return fileFilter; }
    public DestinationCallback getDestination(){ return destination; }
    public FilenameCallback getFilename() { return filename; }
    public boolean isPreservePath() { return preservePath; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long maxSize = 0; // 0 = unlimited
        private List<String> allowedTypes = new ArrayList<>();
        private FileFilter fileFilter = null;
        private DestinationCallback destination = null;
        private FilenameCallback filename = null;
        private boolean preservePath = false;

        /**
         * Maximum allowed file size in bytes. Files exceeding this limit produce a
         * {@link MulterException#LIMIT_FILE_SIZE} error. Default: {@code 0} (unlimited).
         *
         * @param bytes max file size in bytes; {@code 0} means no limit
         * @return this builder
         */
        public Builder maxSize(long bytes) { this.maxSize = bytes; return this; }

        /**
         * Restrict uploads to specific MIME types (e.g., {@code "image/png"}, {@code "image/jpeg"}).
         * Files with a non-matching MIME type are rejected with
         * {@link MulterException#LIMIT_UNEXPECTED_FILE}.
         * An empty list (default) allows all types. Takes lower priority than {@link #fileFilter}.
         *
         * @param types one or more MIME type strings to allow
         * @return this builder
         */
        public Builder allowedTypes(String... types) { for (String t : types) this.allowedTypes.add(t); return this; }

        /**
         * Custom file-filter callback — takes priority over {@link #allowedTypes}.
         *
         * <pre>{@code
         * .fileFilter((req, file, cb) -> {
         *     if (file.mimetype().startsWith("image/")) cb.accept(true);
         *     else cb.reject(new MulterException("LIMIT_UNEXPECTED_FILE", "Images only"));
         * })
         * }</pre>
         *
         * @param cb callback that receives the request, the uploaded file stub, and a
         *           {@link FilterCallback} to accept or reject the file
         * @return this builder
         */
        public Builder fileFilter(FileFilter cb) { this.fileFilter = cb; return this; }

        /**
         * Static destination directory for disk storage. Convenience overload that wraps
         * the directory string in a {@link DestinationCallback}.
         *
         * @param dir path to the upload directory (relative to JVM working directory)
         * @return this builder
         */
        public Builder destination(String dir) { this.destination = (req, file) -> dir; return this; }

        /**
         * Dynamic destination directory callback — called per file to resolve the upload path.
         *
         * <pre>{@code
         * .destination((req, file) -> "uploads/" + req.param("userId"))
         * }</pre>
         *
         * @param cb callback returning the destination directory path for a given file
         * @return this builder
         */
        public Builder destination(DestinationCallback cb) { this.destination = cb; return this; }

        /**
         * Dynamic filename callback — called per file to determine the saved filename on disk.
         * When not set, the {@link DiskStorage} uses the original filename.
         *
         * <pre>{@code
         * .filename((req, file) -> System.currentTimeMillis() + "_" + file.originalName())
         * }</pre>
         *
         * @param cb callback returning the filename (not path) for a given upload
         * @return this builder
         */
        public Builder filename(FilenameCallback cb) { this.filename = cb; return this; }

        /**
         * Preserve the full original path of uploaded files instead of just the filename.
         * Default: {@code false}.
         *
         * @param v {@code true} to keep the full path from the client
         * @return this builder
         */
        public Builder preservePath(boolean v) { this.preservePath = v; return this; }

        public MultipartOptions build() { return new MultipartOptions(this); }
    }
}
