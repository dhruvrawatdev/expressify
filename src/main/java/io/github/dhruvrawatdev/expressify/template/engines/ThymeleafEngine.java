package io.github.dhruvrawatdev.expressify.template.engines;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thymeleaf template engine adapter.
 * Templates use the .html extension and must live in the configured views directory.
 */
public class ThymeleafEngine implements io.github.dhruvrawatdev.expressify.template.TemplateEngine {

    private final Map<String, TemplateEngine> cache = new ConcurrentHashMap<>();

    @Override
    public String render(String viewsDir, String templateName, Map<String, Object> model) throws Exception {
        TemplateEngine engine = cache.computeIfAbsent(viewsDir, dir -> {
            FileTemplateResolver resolver = new FileTemplateResolver();
            resolver.setPrefix(dir.endsWith("/") ? dir : dir + "/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            resolver.setCacheable(false); // disable for development; enable for production
            TemplateEngine te = new TemplateEngine();
            te.setTemplateResolver(resolver);
            return te;
        });

        Context ctx = new Context();
        if (model != null) ctx.setVariables(model);
        return engine.process(templateName, ctx);
    }
}
