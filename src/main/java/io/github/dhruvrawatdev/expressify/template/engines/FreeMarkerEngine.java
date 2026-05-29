package io.github.dhruvrawatdev.expressify.template.engines;

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.github.dhruvrawatdev.expressify.template.TemplateEngine;

import java.io.File;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FreeMarker template engine adapter.
 * Templates use the .ftl extension and must live in the configured views directory.
 */
public class FreeMarkerEngine implements TemplateEngine {

    private final Map<String, Configuration> cache = new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model) throws Exception {
        Configuration cfg = cache.computeIfAbsent(viewsDir, dir -> {
            Configuration c = new Configuration(Configuration.VERSION_2_3_34);
            try {
                c.setTemplateLoader(new freemarker.cache.FileTemplateLoader(new File(dir)));
            } catch (Exception e) {
                throw new RuntimeException("FreeMarker: could not set template directory: " + dir, e);
            }
            c.setDefaultEncoding("UTF-8");
            return c;
        });

        Template template = cfg.getTemplate(templateName + ".ftl");
        StringWriter writer = new StringWriter();
        template.process(model != null ? model : Map.of(), writer);
        return writer.toString();
    }
}
