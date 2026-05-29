package io.github.dhruvrawatdev.expressify.template;

import java.util.Map;

/**
 * Contract for all template engines in Expressify — built-in and custom.
 *
 * <p>Expressify ships four built-in engines selectable by name via
 * {@code app.set("view engine", name)}:
 * <ul>
 *   <li>{@code "thymeleaf"} (default)</li>
 *   <li>{@code "pebble"}</li>
 *   <li>{@code "freemarker"}</li>
 *   <li>{@code "jte"}</li>
 * </ul>
 *
 * <h2>Custom engine — lambda / callback pattern</h2>
 * <p>This is a single-method interface, so any engine can be registered as a lambda —
 * the direct Java equivalent of Express.js {@code app.engine('ext', callbackFn)}.
 * The key difference: Expressify engines return the rendered string synchronously
 * instead of calling a Node-style {@code callback(err, html)}.
 *
 * <pre>{@code
 * // Register a trivial token-substitution engine
 * app.engine("ntl", (viewsDir, templateName, model) -> {
 *     String content = Files.readString(Path.of(viewsDir, templateName + ".ntl"));
 *     return content
 *         .replace("#title#",   "<title>" + model.get("title")   + "</title>")
 *         .replace("#message#", "<h1>"    + model.get("message") + "</h1>");
 * });
 * app.set("views", "./views");       // directory that contains *.ntl files
 * app.set("view engine", "ntl");     // make it the default engine
 *
 * // Render in a route — looks up views/index.ntl
 * app.get("/", (req, res) -> {
 *     res.render("index", Map.of(
 *         "title",   "Hello World",
 *         "message", "Welcome to Expressify"
 *     ));
 * });
 * }</pre>
 *
 * <h2>Express.js equivalent (for reference)</h2>
 * <pre>{@code
 * const fs = require('fs');
 * app.engine('ntl', (filePath, options, callback) => {
 *     fs.readFile(filePath, (err, content) => {
 *         if (err) return callback(err);
 *         const rendered = content.toString()
 *             .replace('#title#',   `<title>${options.title}</title>`)
 *             .replace('#message#', `<h1>${options.message}</h1>`);
 *         return callback(null, rendered);
 *     });
 * });
 * app.set('views', './views');
 * app.set('view engine', 'ntl');
 * }</pre>
 *
 * @see io.github.dhruvrawatdev.expressify.Expressify#engine(String, TemplateEngine)
 */
@FunctionalInterface
public interface TemplateEngine {

    /**
     * Render a template and return the fully-rendered output as a string.
     *
     * @param viewsDir     absolute or relative path to the views directory,
     *                     as configured by {@code app.set("views", path)}
     * @param templateName template name without file extension
     *                     (e.g., {@code "index"} resolves to {@code views/index.ntl})
     * @param model        key/value pairs made available to the template as variables
     * @return the rendered HTML (or text) output
     * @throws Exception if the template is missing, unreadable, or rendering fails;
     *                   Expressify forwards the exception to the error-handler chain
     */
    String render(String viewsDir, String templateName, Map<String, Object> model) throws Exception;
}
