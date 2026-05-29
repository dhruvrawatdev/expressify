package io.github.dhruvrawatdev.expressify.middleware.serve_index;

import java.util.function.Predicate;

/**
 * Configuration options for {@link ServeIndex} directory listing middleware.
 *
 * <pre>{@code
 * app.use(ServeIndex.directory("public", ServeIndexOptions.builder()
 *     .hidden(true)                      // show dot-files
 *     .filter(name -> !name.endsWith(".tmp"))  // hide .tmp files
 *     .build()));
 * }</pre>
 */
public final class ServeIndexOptions {

    private final boolean hidden;
    private final Predicate<String> filter;

    private ServeIndexOptions(Builder b) {
        this.hidden = b.hidden;
        this.filter = b.filter;
    }

    /**
     * Whether to include hidden files (names starting with {@code .}) in the listing.
     * Default: {@code false}.
     */
    public boolean isHidden() { return hidden; }

    /**
     * Optional predicate applied to each filename. Return {@code true} to include the
     * file in the listing, {@code false} to exclude it. {@code null} means include all.
     */
    public Predicate<String> getFilter() { return filter; }

    /** Create a builder for {@link ServeIndexOptions}. */
    public static Builder builder() { return new Builder(); }

    /** Return the default options (no hidden files, no filter). */
    public static ServeIndexOptions defaults() { return builder().build(); }

    /** Builder for {@link ServeIndexOptions}. */
    public static final class Builder {

        private boolean           hidden = false;
        private Predicate<String> filter = null;

        /**
         * Show files and directories whose names begin with {@code .}.
         * Default: {@code false}.
         *
         * @param hidden {@code true} to list hidden entries
         * @return this builder
         */
        public Builder hidden(boolean hidden) { this.hidden = hidden; return this; }

        /**
         * Apply a custom filename filter. Only files for which the predicate returns
         * {@code true} appear in the listing.
         *
         * <pre>{@code
         * .filter(name -> !name.endsWith(".log"))  // hide log files
         * }</pre>
         *
         * @param filter predicate on filenames; {@code null} disables filtering
         * @return this builder
         */
        public Builder filter(Predicate<String> filter) { this.filter = filter; return this; }

        /** Build the {@link ServeIndexOptions}. */
        public ServeIndexOptions build() { return new ServeIndexOptions(this); }
    }
}
