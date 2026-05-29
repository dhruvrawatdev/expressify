package io.github.dhruvrawatdev.expressify.middleware.multer;

/**
 * Metadata for a file uploaded via multipart form-data (populated by Multer middleware).
 * Mirrors the file object populated by the multer npm package.
 *
 * <p>Access via {@code Multer.file(req)} or {@code Multer.files(req)}.
 */
public class UploadedFile {

    private final String fieldName;
    private final String originalName;
    private final String mimetype;
    private final String encoding;
    private final long   size;

    // DiskStorage fields
    private final String destination;
    private final String filename;
    private final String path;

    // MemoryStorage field
    private final byte[] buffer;

    private UploadedFile(Builder b) {
        this.fieldName = b.fieldName;
        this.originalName = b.originalName;
        this.mimetype = b.mimetype;
        this.encoding = b.encoding;
        this.size = b.size;
        this.destination = b.destination;
        this.filename = b.filename;
        this.path = b.path;
        this.buffer = b.buffer;
    }

    public String fieldName() { return fieldName; }
    public String originalName() { return originalName; }
    public String mimetype() { return mimetype; }
    public String encoding() { return encoding; }
    public long size() { return size; }
    public String destination()  { return destination; }
    public String filename() { return filename; }
    public String path() { return path; }
    public byte[] buffer() { return buffer; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String fieldName;
        private String originalName;
        private String mimetype;
        private String encoding = "7bit";
        private long size;
        private String destination;
        private String filename;
        private String path;
        private byte[] buffer;

        public Builder fieldName(String v) { this.fieldName = v; return this; }
        public Builder originalName(String v) { this.originalName = v; return this; }
        public Builder mimetype(String v) { this.mimetype = v; return this; }
        public Builder encoding(String v) { this.encoding = v; return this; }
        public Builder size(long v) { this.size = v; return this; }
        public Builder destination(String v) { this.destination = v; return this; }
        public Builder filename(String v) { this.filename = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder buffer(byte[] v) { this.buffer = v; return this; }

        public UploadedFile build() { return new UploadedFile(this); }
    }

    @Override
    public String toString() {
        return "UploadedFile{field=" + fieldName + ", name=" + originalName
                + ", mime=" + mimetype + ", size=" + size + "}";
    }
}
