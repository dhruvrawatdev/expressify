package io.github.dhruvrawatdev.expressify.middleware.multer;

import io.github.dhruvrawatdev.expressify.http.Request;
import io.github.dhruvrawatdev.expressify.http.Response;
import io.github.dhruvrawatdev.expressify.router.handler.NextFunction;
import io.github.dhruvrawatdev.expressify.router.handler.RouteHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;

import java.nio.file.Paths;
import java.util.*;

/**
 * Multipart file-upload middleware using Undertow's built-in parser.
 * Mirrors the multer npm package for Express.js.
 *
 * <pre>{@code
 * // Disk storage — save to /uploads
 * Multer upload = Multer.diskStorage(MultipartOptions.builder()
 *     .destination((req, file) -> "uploads")
 *     .filename((req, file) -> System.currentTimeMillis() + "_" + file.originalName())
 *     .maxSize(5 * 1024 * 1024)
 *     .build());
 *
 * app.post("/upload", upload.single("avatar"), (req, res) -> {
 *     res.json(Map.of("file", Multer.file(req, "avatar").path()));
 * });
 *
 * // Memory storage
 * Multer mem = Multer.memoryStorage(MultipartOptions.builder().build());
 * app.post("/upload", mem.any(), (req, res) -> {
 *     byte[] bytes = Multer.files(req).get(0).buffer();
 *     res.json(Map.of("size", bytes.length));
 * });
 * }</pre>
 */
public class Multer {

    private static final String FILES_KEY = "_multerFiles";

    private final StorageEngine storage;
    private final MultipartOptions opts;

    private Multer(StorageEngine storage, MultipartOptions opts) {
        this.storage = storage;
        this.opts = opts;
    }

    // Factory methods

    /**
     * Create a Multer instance that saves uploaded files to disk.
     *
     * <pre>{@code
     * Multer upload = Multer.diskStorage(MultipartOptions.builder()
     *     .destination((req, file) -> "uploads/" + req.param("userId"))
     *     .filename((req, file) -> System.currentTimeMillis() + "_" + file.originalName())
     *     .maxSize(10 * 1024 * 1024)   // 10 MB per file
     *     .allowedTypes("image/png", "image/jpeg")
     *     .build());
     * }</pre>
     *
     * @param opts disk-storage configuration; build with {@link MultipartOptions#builder()}
     * @return a {@link Multer} instance whose handler methods ({@link #single}, {@link #array}, etc.)
     *         produce middleware that writes uploads to the configured directory
     */
    public static Multer diskStorage(MultipartOptions opts) {
        return new Multer(new DiskStorage(opts), opts);
    }

    /**
     * Create a Multer instance that buffers uploaded files entirely in memory.
     *
     * <p>Use for small files that need to be processed in-process (e.g., image resizing,
     * virus scanning). For large uploads prefer {@link #diskStorage(MultipartOptions)}.
     *
     * @param opts memory-storage configuration; build with {@link MultipartOptions#builder()}
     * @return a {@link Multer} instance that keeps file bytes in {@link UploadedFile#buffer()}
     */
    public static Multer memoryStorage(MultipartOptions opts) {
        return new Multer(new MemoryStorage(opts), opts);
    }

    /** Convenience builder for a named field descriptor. */
    public static FieldSpec field(String name, int maxCount) {
        return new FieldSpec(name, maxCount);
    }

    // Handler factories

    /**
     * Accept exactly one file from the named form field.
     *
     * <p>Errors with {@link MulterException#LIMIT_FILE_COUNT} if more than one file
     * is uploaded for that field. After the middleware runs, retrieve the file with
     * {@link Multer#file(Request, String)}.
     *
     * <pre>{@code
     * app.post("/avatar", upload.single("avatar"), (req, res) -> {
     *     UploadedFile file = Multer.file(req, "avatar");
     *     res.json(Map.of("path", file.path(), "size", file.size()));
     * });
     * }</pre>
     *
     * @param fieldName the HTML input {@code name} attribute of the file field
     * @return a {@link RouteHandler} that parses the multipart body and stores the single upload
     */
    public RouteHandler single(String fieldName) {
        return (req, res, next) -> process(req, res, next, Mode.SINGLE, fieldName, 1, null);
    }

    /**
     * Accept up to {@code maxCount} files from the named form field.
     *
     * <p>After the middleware runs, retrieve files with {@link Multer#files(Request, String)}.
     *
     * @param fieldName the HTML input {@code name} attribute of the file field
     * @param maxCount  maximum number of files allowed from this field;
     *                  exceeding it produces a {@link MulterException#LIMIT_FILE_COUNT} error
     * @return a {@link RouteHandler} that parses the multipart body and stores all matching uploads
     */
    public RouteHandler array(String fieldName, int maxCount) {
        return (req, res, next) -> process(req, res, next, Mode.ARRAY, fieldName, maxCount, null);
    }

    /**
     * Accept files from multiple named form fields, each with its own count limit.
     *
     * <pre>{@code
     * app.post("/upload", upload.fields(
     *     Multer.field("avatar",    1),
     *     Multer.field("documents", 5)
     * ), (req, res) -> {
     *     UploadedFile avatar = Multer.file(req, "avatar");
     *     List<UploadedFile> docs = Multer.files(req, "documents");
     * });
     * }</pre>
     *
     * @param specs field descriptors; create each with {@link Multer#field(String, int)}
     * @return a {@link RouteHandler} that validates and stores files according to the field specs
     */
    public RouteHandler fields(FieldSpec... specs) {
        return (req, res, next) -> process(req, res, next, Mode.FIELDS, null, -1, Arrays.asList(specs));
    }

    /**
     * Accept only text form fields — errors with {@link MulterException#LIMIT_UNEXPECTED_FILE}
     * if the client uploads any file.
     *
     * @return a {@link RouteHandler} that parses only text fields from the multipart body
     */
    public RouteHandler none() {
        return (req, res, next) -> process(req, res, next, Mode.NONE, null, -1, null);
    }

    /**
     * Accept files from any form field without field-name restrictions.
     * Retrieve all files with {@link Multer#files(Request)}.
     *
     * @return a {@link RouteHandler} that stores all uploaded files regardless of field name
     */
    public RouteHandler any() {
        return (req, res, next) -> process(req, res, next, Mode.ANY, null, -1, null);
    }

    // ── Core processing ────────────────────────────────────────────────────

    private void process(Request req, Response res, NextFunction next,
                         Mode mode, String targetField, int maxCount,
                         List<FieldSpec> fieldSpecs) throws Exception {

        String ct = req.get("Content-Type");
        if (ct == null || !ct.contains("multipart/form-data")) {
            next.run();
            return;
        }

        // Undertow built-in multipart parser — no Apache Commons needed
        FormParserFactory factory = FormParserFactory.builder()
                .addParsers(new MultiPartParserDefinition(
                        Paths.get(System.getProperty("java.io.tmpdir"))))
                .build();

        FormDataParser parser = factory.createParser(req.getExchange());
        if (parser == null) { next.run(); return; }

        FormData formData = parser.parseBlocking();

        Map<String, List<UploadedFile>> uploadedFiles = new LinkedHashMap<>();
        Map<String, List<String>> textFields = new LinkedHashMap<>();
        Map<String, Integer> fieldCounts = new HashMap<>();

        for (String fieldName : formData) {
            Deque<FormData.FormValue> values = formData.get(fieldName);
            for (FormData.FormValue value : values) {

                if (value.isFileItem()) {
                    // File validation

                    if (mode == Mode.NONE) {
                        next.error(new MulterException(MulterException.LIMIT_UNEXPECTED_FILE,
                                "File upload not expected (upload.none())").field(fieldName));
                        return;
                    }
                    if ((mode == Mode.SINGLE || mode == Mode.ARRAY)
                            && targetField != null && !targetField.equals(fieldName)) {
                        next.error(new MulterException(MulterException.LIMIT_UNEXPECTED_FILE,
                                "Unexpected field: " + fieldName).field(fieldName));
                        return;
                    }
                    if (mode == Mode.FIELDS && fieldSpecs != null) {
                        boolean known = fieldSpecs.stream().anyMatch(s -> s.name.equals(fieldName));
                        if (!known) {
                            next.error(new MulterException(MulterException.LIMIT_UNEXPECTED_FILE,
                                    "Unexpected field: " + fieldName).field(fieldName));
                            return;
                        }
                    }

                    int nextCount = fieldCounts.getOrDefault(fieldName, 0) + 1;

                    if (mode == Mode.SINGLE && nextCount > 1) {
                        next.error(new MulterException(MulterException.LIMIT_FILE_COUNT,
                                "Field '" + fieldName + "' exceeds single-file limit").field(fieldName));
                        return;
                    }
                    if (mode == Mode.ARRAY && targetField != null
                            && targetField.equals(fieldName) && maxCount > 0 && nextCount > maxCount) {
                        next.error(new MulterException(MulterException.LIMIT_FILE_COUNT,
                                "Field '" + fieldName + "' exceeded maxCount of " + maxCount).field(fieldName));
                        return;
                    }
                    if (mode == Mode.FIELDS && fieldSpecs != null) {
                        for (FieldSpec spec : fieldSpecs) {
                            if (spec.name.equals(fieldName) && nextCount > spec.maxCount) {
                                next.error(new MulterException(MulterException.LIMIT_FILE_COUNT,
                                        "Field '" + spec.name + "' exceeded maxCount of " + spec.maxCount).field(fieldName));
                                return;
                            }
                        }
                    }
                    fieldCounts.put(fieldName, nextCount);

                    // Extract metadata

                    String originalName = extractFilename(value.getHeaders().getFirst("Content-Disposition"));
                    if (originalName == null || originalName.isBlank()) originalName = fieldName;
                    String mimetype = value.getHeaders().getFirst("Content-Type");
                    if (mimetype == null) mimetype = "application/octet-stream";

                    FormData.FileItem fileItem = value.getFileItem();
                    long fileSize = fileItem.getFileSize();

                    // Pre-check size
                    long maxSize = opts.getMaxSize();
                    if (maxSize > 0 && fileSize > 0 && fileSize > maxSize) {
                        next.error(new MulterException(MulterException.LIMIT_FILE_SIZE,
                                "File exceeds max size of " + maxSize + " bytes").field(fieldName));
                        return;
                    }

                    // fileFilter

                    if (opts.getFileFilter() != null) {
                        UploadedFile stub = UploadedFile.builder()
                                .fieldName(fieldName).originalName(originalName)
                                .mimetype(mimetype).size(fileSize).build();
                        boolean[] accepted = {true};
                        MulterException[] rejected = {null};
                        opts.getFileFilter().check(req, stub, new MultipartOptions.FilterCallback() {
                            public void accept(boolean v) { accepted[0] = v; }
                            public void reject(MulterException e) { rejected[0] = e; accepted[0] = false; }
                        });
                        if (rejected[0] != null) { next.error(rejected[0]); return; }
                        if (!accepted[0]) continue;
                    } else if (!opts.getAllowedTypes().isEmpty()) {
                        if (!opts.getAllowedTypes().contains(mimetype)) {
                            next.error(new MulterException(MulterException.LIMIT_UNEXPECTED_FILE,
                                    "MIME type not allowed: " + mimetype).field(fieldName));
                            return;
                        }
                    }

                    // Store

                    UploadedFile uploaded = storage.store(
                            req, fieldName, originalName, mimetype, "7bit",
                            fileSize, fileItem.getInputStream());

                    if (maxSize > 0 && uploaded.size() > maxSize) {
                        next.error(new MulterException(MulterException.LIMIT_FILE_SIZE,
                                "File exceeds max size of " + maxSize + " bytes").field(fieldName));
                        return;
                    }

                    uploadedFiles.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(uploaded);

                } else {
                    // Text field
                    textFields.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(value.getValue());
                }
            }
        }

        req.locals().put(FILES_KEY, uploadedFiles);
        req.setFormFields(textFields);
        next.run();
    }

    // Helpers

    private static String extractFilename(String contentDisposition) {
        if (contentDisposition == null) return null;
        // Try filename* (RFC 5987) first
        for (String part : contentDisposition.split(";")) {
            part = part.trim();
            if (part.startsWith("filename=")) {
                String name = part.substring("filename=".length()).trim();
                if (name.startsWith("\"") && name.endsWith("\""))
                    name = name.substring(1, name.length() - 1);
                return name;
            }
        }
        return null;
    }

    // Static accessors (mirrors Express.js req.file / req.files)

    /**
     * Return the first uploaded file across all fields.
     * Use after {@link #single(String)} or {@link #any()}.
     *
     * @param req the current request
     * @return the first {@link UploadedFile}, or {@code null} if no file was uploaded
     */
    @SuppressWarnings("unchecked")
    public static UploadedFile file(Request req) {
        Map<String, List<UploadedFile>> map =
                (Map<String, List<UploadedFile>>) req.locals().get(FILES_KEY);
        if (map == null) return null;
        for (List<UploadedFile> list : map.values()) {
            if (!list.isEmpty()) return list.get(0);
        }
        return null;
    }

    /**
     * Return the first uploaded file for the given field name.
     * Use after {@link #single(String)}.
     *
     * @param req       the current request
     * @param fieldName the form field name as configured in {@link #single(String)}
     * @return the {@link UploadedFile} for that field, or {@code null} if none was uploaded
     */
    @SuppressWarnings("unchecked")
    public static UploadedFile file(Request req, String fieldName) {
        Map<String, List<UploadedFile>> map =
                (Map<String, List<UploadedFile>>) req.locals().get(FILES_KEY);
        if (map == null) return null;
        List<UploadedFile> list = map.get(fieldName);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    /**
     * Return all uploaded files for the given field name.
     * Use after {@link #array(String, int)}.
     *
     * @param req       the current request
     * @param fieldName the form field name as configured in {@link #array(String, int)}
     * @return an unmodifiable list of {@link UploadedFile} objects; empty if none uploaded
     */
    @SuppressWarnings("unchecked")
    public static List<UploadedFile> files(Request req, String fieldName) {
        Map<String, List<UploadedFile>> map =
                (Map<String, List<UploadedFile>>) req.locals().get(FILES_KEY);
        if (map == null) return Collections.emptyList();
        return map.getOrDefault(fieldName, Collections.emptyList());
    }

    /**
     * Return all uploaded files across every field.
     * Use after {@link #fields(FieldSpec...)} or {@link #any()}.
     *
     * @param req the current request
     * @return a flat list of all {@link UploadedFile} objects from all fields; empty if none
     */
    @SuppressWarnings("unchecked")
    public static List<UploadedFile> files(Request req) {
        Map<String, List<UploadedFile>> map =
                (Map<String, List<UploadedFile>>) req.locals().get(FILES_KEY);
        if (map == null) return Collections.emptyList();
        List<UploadedFile> all = new ArrayList<>();
        map.values().forEach(all::addAll);
        return all;
    }

    // Supporting types

    public static class FieldSpec {
        public final String name;
        public final int    maxCount;
        public FieldSpec(String name, int maxCount) { this.name = name; this.maxCount = maxCount; }
    }

    private enum Mode { SINGLE, ARRAY, FIELDS, NONE, ANY }
}
