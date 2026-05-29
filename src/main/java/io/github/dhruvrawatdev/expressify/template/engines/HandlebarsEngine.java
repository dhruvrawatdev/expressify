package io.github.dhruvrawatdev.expressify.template.engines;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handlebars template engine adapter.
 * Templates use the .hbs extension and must live in the configured views directory.
 *
 * <pre>{@code
 * app.set("view engine", "handlebars");
 * res.render("index", Map.of("name", "World"));
 * }</pre>
 */
public class HandlebarsEngine implements TemplateEngine {

    private final Map<String, Handlebars> cache = new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model) throws Exception {
        String absDir = Path.of(viewsDir).isAbsolute() ? viewsDir
                : Path.of(System.getProperty("user.dir"), viewsDir).toString();

        Handlebars handlebars = cache.computeIfAbsent(absDir, dir -> {
            FileTemplateLoader loader = new FileTemplateLoader(new File(dir), ".hbs");
            return new Handlebars(loader);
        });

        Template template = handlebars.compile(templateName);
        return template.apply(model != null ? model : Map.of());
    }
}
