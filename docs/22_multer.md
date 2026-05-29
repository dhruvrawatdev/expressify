# 22 — Multer (File Uploads)

Multer handles `multipart/form-data` requests — the format used when HTML forms upload files.

---

## Quick start

```java
import io.github.dhruvrawatdev.expressify.middleware.multer.*;

// Save files to disk
Multer upload = Multer.create(
    MultipartOptions.builder()
        .storage(DiskStorage.create("uploads/"))
        .build()
);

// Single file upload
app.post("/upload", upload.single("file"), (req, res) -> {
    UploadedFile file = req.file("file");
    res.json(Map.of(
        "name", file.originalName(),
        "size", file.size(),
        "path", file.path()
    ));
});
```

HTML form:
```html
<form method="POST" action="/upload" enctype="multipart/form-data">
    <input type="file" name="file">
    <button type="submit">Upload</button>
</form>
```

---

## Storage types

### Disk storage — save to a folder

```java
// Fixed destination folder
DiskStorage disk = DiskStorage.create("uploads/");

// Custom destination per file
DiskStorage disk = DiskStorage.create(
    MultipartOptions.DestinationCallback.of((req, file) -> {
        return "uploads/" + file.fieldName() + "/";
    }),
    MultipartOptions.FilenameCallback.of((req, file) -> {
        // Custom filename
        return System.currentTimeMillis() + "-" + file.originalName();
    })
);
```

### Memory storage — keep in memory as byte[]

```java
MemoryStorage memory = MemoryStorage.create();

Multer upload = Multer.create(
    MultipartOptions.builder()
        .storage(memory)
        .build()
);

app.post("/upload", upload.single("avatar"), (req, res) -> {
    UploadedFile file = req.file("avatar");
    byte[] bytes = file.bytes();  // the file as a byte array
    // process bytes...
    res.send("Received " + bytes.length + " bytes");
});
```

Use memory storage when you want to process the file in code (resize image, validate, etc.) without saving to disk first.

---

## Single file

```java
// Upload one file with field name "avatar"
app.post("/profile", upload.single("avatar"), (req, res) -> {
    UploadedFile avatar = req.file("avatar");

    if (avatar == null) {
        res.status(400).send("No file uploaded");
        return;
    }

    System.out.println("Name:     " + avatar.originalName());
    System.out.println("Size:     " + avatar.size() + " bytes");
    System.out.println("Type:     " + avatar.mimeType());
    System.out.println("Saved at: " + avatar.path());
    System.out.println("Field:    " + avatar.fieldName());

    res.json(Map.of("uploaded", avatar.originalName()));
});
```

---

## Multiple files, same field

```java
// Upload up to 5 files with field name "photos"
app.post("/gallery", upload.array("photos", 5), (req, res) -> {
    List<UploadedFile> photos = req.files("photos");
    List<String> names = photos.stream()
        .map(UploadedFile::originalName)
        .collect(Collectors.toList());
    res.json(Map.of("uploaded", names));
});
```

---

## Multiple files, different fields

```java
app.post("/document",
    upload.fields(
        Multer.field("resume", 1),    // 1 resume
        Multer.field("photos", 5)     // up to 5 photos
    ),
    (req, res) -> {
        UploadedFile resume = req.file("resume");
        List<UploadedFile> photos = req.files("photos");
        res.json(Map.of(
            "resume", resume.originalName(),
            "photoCount", photos.size()
        ));
    }
);
```

---

## Any files

```java
// Accept any fields with any number of files
app.post("/upload-all", upload.any(), (req, res) -> {
    List<UploadedFile> allFiles = req.files();
    res.json(Map.of("count", allFiles.size()));
});
```

---

## Text fields alongside files

```java
// If the form has both text fields and files
app.post("/profile", upload.single("avatar"), (req, res) -> {
    UploadedFile avatar = req.file("avatar");
    String name    = req.body("name");     // text field
    String bio     = req.body("bio");      // text field

    res.json(Map.of(
        "name",   name,
        "bio",    bio,
        "avatar", avatar != null ? avatar.originalName() : "none"
    ));
});
```

---

## File size limits

```java
Multer upload = Multer.create(
    MultipartOptions.builder()
        .storage(DiskStorage.create("uploads/"))
        .limits(MultipartOptions.Limits.builder()
            .fileSize(5 * 1024 * 1024)   // max 5 MB per file
            .files(10)                    // max 10 files total
            .build()
        )
        .build()
);
```

---

## File type filter

Only allow certain file types:

```java
Multer upload = Multer.create(
    MultipartOptions.builder()
        .storage(DiskStorage.create("uploads/images/"))
        .fileFilter((req, file, callback) -> {
            String mime = file.mimeType();
            if (mime.startsWith("image/")) {
                callback.accept(true, null);   // allow
            } else {
                callback.accept(false, new MulterException("Only images allowed"));
            }
        })
        .build()
);
```

Handle the error:

```java
app.error((err, req, res, next) -> {
    if (err instanceof MulterException) {
        res.status(400).json(Map.of("error", err.getMessage()));
    } else {
        res.status(500).json(Map.of("error", "Upload failed"));
    }
});
```

---

## `UploadedFile` — what you get

| Method | Returns | Description |
|---|---|---|
| `originalName()` | String | Original filename from user's computer |
| `fieldName()` | String | Form field name (`"avatar"`, `"photos"`, etc.) |
| `mimeType()` | String | MIME type (`"image/jpeg"`, `"application/pdf"`) |
| `size()` | long | File size in bytes |
| `path()` | String | Saved file path (DiskStorage only) |
| `bytes()` | byte[] | File bytes (MemoryStorage only) |
| `encoding()` | String | Transfer encoding |

---

## Accessing uploaded files

| Method | When to use |
|---|---|
| `req.file("fieldName")` | Single file upload |
| `req.files("fieldName")` | Multiple files with same field name |
| `req.files()` | All uploaded files (any field) |

---

## Next steps

- [23 — Serve Static](23_serve_static.md)
- [05 — Request Object](05_request.md)
