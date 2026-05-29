package io.github.dhruvrawatdev.expressify.middleware.method_override;

import java.util.List;

/**
 * Configuration options for the MethodOverride middleware.
 */
public class MethodOverrideOptions {

    private final List<String> methods;

    private MethodOverrideOptions(Builder b) {
        this.methods = b.methods;
    }

    public static MethodOverrideOptions defaults() {
        return new Builder().build();
    }

    /** HTTP methods on which the override is honoured (default: POST only). */
    public List<String> getMethods() { return methods; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<String> methods = List.of("POST");

        /** Set the allowed originating methods (e.g. List.of("POST", "PUT")). */
        public Builder methods(List<String> methods) { this.methods = methods; return this; }

        public MethodOverrideOptions build() { return new MethodOverrideOptions(this); }
    }
}
