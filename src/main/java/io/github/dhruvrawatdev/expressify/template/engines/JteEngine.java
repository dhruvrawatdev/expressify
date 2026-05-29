package io.github.dhruvrawatdev.expressify.template.engines;

import gg.jte.ContentType;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JTE (Java Template Engine) adapter using dynamic compilation.
 *
 * <p>Templates use the .jte extension. Example:
 * <pre>
 *   &#64;param String username
 *   &#64;param String engine
 *   &lt;h1&gt;Hello, ${username}!&lt;/h1&gt;
 * </pre>
 *
 * <p>The model map keys must match the {@code @param} names in the template.
 * Dynamic compilation requires a JDK at runtime (available when running via Maven).
 */
public class JteEngine implements TemplateEngine {

    private final Map<String, gg.jte.TemplateEngine> cache = new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model)
            throws Exception {

        // Resolve to an absolute path so DirectoryCodeResolver can locate sources
        Path sourceDir = Paths.get(viewsDir).isAbsolute()
                ? Paths.get(viewsDir)
                : Paths.get(System.getProperty("user.dir"), viewsDir).toAbsolutePath();

        gg.jte.TemplateEngine engine = cache.computeIfAbsent(sourceDir.toString(), dir -> {
            DirectoryCodeResolver resolver = new DirectoryCodeResolver(Path.of(dir));
            Path classDir = Paths.get(System.getProperty("java.io.tmpdir"),
                    "expressify-jte-classes");
            try {
                java.nio.file.Files.createDirectories(classDir);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Cannot create JTE class output directory: " + classDir, e);
            }
            return gg.jte.TemplateEngine.create(
                    resolver,
                    classDir,
                    ContentType.Html,
                    Thread.currentThread().getContextClassLoader()
            );
        });

        StringOutput output = new StringOutput();
        engine.render(templateName + ".jte", model != null ? model : Map.of(), output);
        return output.toString();
    }
}
