package io.github.dhruvrawatdev.expressify.template.engines;

import io.github.dhruvrawatdev.expressify.template.TemplateEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pebble template engine adapter.
 * Supports both .pebble and .html template extensions.
 * Templates must live in the configured views directory.
 */
public class PebbleEngine implements TemplateEngine {

    // One engine per views directory, keyed by absolute path
    private final Map<String, io.pebbletemplates.pebble.PebbleEngine> engineCache =
            new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model)
            throws Exception {

        String absDir = Path.of(viewsDir).isAbsolute() ? viewsDir
                : Path.of(System.getProperty("user.dir"), viewsDir).toString();

        // Determine extension: prefer .html, fall back to .pebble
        String ext;
        if (Files.exists(Path.of(absDir, templateName + ".html"))) {
            ext = ".html";
        } else if (Files.exists(Path.of(absDir, templateName + ".pebble"))) {
            ext = ".pebble";
        } else {
            throw new java.io.FileNotFoundException(
                    "Pebble template not found: tried '" + templateName + ".html' and '"
                    + templateName + ".pebble' in " + absDir);
        }

        // One Pebble engine per views directory. FileLoader prefix must be a non-empty
        // directory path — Pebble 4.x throws LoaderException if prefix is blank.
        // Suffix is set to "" so we can pass the full filename including extension.
        io.pebbletemplates.pebble.PebbleEngine engine = engineCache.computeIfAbsent(absDir, dir -> {
            // Normalise to forward slashes; Pebble's FileLoader requires a trailing separator
            String prefix = dir.replace('\\', '/');
            if (!prefix.endsWith("/")) prefix += "/";
            FileLoader loader = new FileLoader(prefix);
            loader.setSuffix("");
            return new io.pebbletemplates.pebble.PebbleEngine.Builder()
                    .loader(loader)
                    .cacheActive(false)
                    .build();
        });

        // Template name relative to the views directory (forward slashes, with extension)
        String templatePath = templateName.replace('\\', '/') + ext;

        PebbleTemplate template = engine.getTemplate(templatePath);
        StringWriter writer = new StringWriter();
        template.evaluate(writer, model != null ? model : Map.of());
        String result = writer.toString();
        if (result.isEmpty()) {
            throw new RuntimeException(
                    "Pebble template '" + templateName + ext + "' rendered empty output — "
                    + "check for missing model variables or syntax errors in: " + templatePath);
        }
        return result;
    }
}
