package io.github.dhruvrawatdev.expressify.template.engines;

import io.github.dhruvrawatdev.expressify.template.TemplateEngine;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.runtime.RuntimeConstants;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Velocity template engine adapter.
 * Templates use the .vm extension and must live in the configured views directory.
 *
 * <pre>{@code
 * app.set("view engine", "velocity");
 * res.render("index", Map.of("name", "World"));
 * }</pre>
 */
public class VelocityEngine implements TemplateEngine {

    private final Map<String, org.apache.velocity.app.VelocityEngine> cache = new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model) throws Exception {
        String absDir = Path.of(viewsDir).isAbsolute() ? viewsDir
                : Path.of(System.getProperty("user.dir"), viewsDir).toString();

        org.apache.velocity.app.VelocityEngine ve = cache.computeIfAbsent(absDir, dir -> {
            Properties props = new Properties();
            props.setProperty(RuntimeConstants.RESOURCE_LOADERS, "file");
            props.setProperty("resource.loader.file.class",
                    "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
            props.setProperty("resource.loader.file.path", dir);
            props.setProperty("resource.loader.file.cache", "false");
            props.setProperty(RuntimeConstants.INPUT_ENCODING, "UTF-8");
            props.setProperty("runtime.log.instance", "");
            org.apache.velocity.app.VelocityEngine engine =
                    new org.apache.velocity.app.VelocityEngine(props);
            engine.init();
            return engine;
        });

        VelocityContext context = new VelocityContext();
        if (model != null) {
            model.forEach(context::put);
        }

        org.apache.velocity.Template template = ve.getTemplate(templateName + ".vm", "UTF-8");
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }
}
