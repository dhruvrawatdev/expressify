package io.github.dhruvrawatdev.expressify.middleware.helmet;

import java.util.List;
import java.util.Map;

/**
 * Configuration for {@link Helmet} security-header middleware.
 * Mirrors all options from the {@code helmet} npm package.
 */
public class HelmetOptions {

    private final boolean contentSecurityPolicy;
    private final Map<String, List<String>>  cspDirectives;
    private final boolean crossOriginEmbedderPolicy;
    private final String  crossOriginOpenerPolicy;
    private final String  crossOriginResourcePolicy;
    private final boolean dnsPrefetchControl;
    private final boolean dnsPrefetchControlAllow;
    private final String  frameguard;
    private final boolean hidePoweredBy;
    private final boolean hsts;
    private final int hstsMaxAge;
    private final boolean hstsIncludeSubDomains;
    private final boolean hstsPreload;
    private final boolean ieNoOpen;
    private final boolean noSniff;
    private final boolean originAgentCluster;
    private final String  permittedCrossDomainPolicies;
    private final String  referrerPolicy;
    private final boolean xssProtection;

    private HelmetOptions(Builder b) {
        this.contentSecurityPolicy = b.contentSecurityPolicy;
        this.cspDirectives = b.cspDirectives;
        this.crossOriginEmbedderPolicy = b.crossOriginEmbedderPolicy;
        this.crossOriginOpenerPolicy = b.crossOriginOpenerPolicy;
        this.crossOriginResourcePolicy = b.crossOriginResourcePolicy;
        this.dnsPrefetchControl = b.dnsPrefetchControl;
        this.dnsPrefetchControlAllow = b.dnsPrefetchControlAllow;
        this.frameguard = b.frameguard;
        this.hidePoweredBy = b.hidePoweredBy;
        this.hsts = b.hsts;
        this.hstsMaxAge = b.hstsMaxAge;
        this.hstsIncludeSubDomains = b.hstsIncludeSubDomains;
        this.hstsPreload = b.hstsPreload;
        this.ieNoOpen = b.ieNoOpen;
        this.noSniff = b.noSniff;
        this.originAgentCluster = b.originAgentCluster;
        this.permittedCrossDomainPolicies = b.permittedCrossDomainPolicies;
        this.referrerPolicy = b.referrerPolicy;
        this.xssProtection = b.xssProtection;
    }

    public boolean isContentSecurityPolicy() { return contentSecurityPolicy; }
    public Map<String, List<String>> getCspDirectives() { return cspDirectives; }
    public boolean isCrossOriginEmbedderPolicy() { return crossOriginEmbedderPolicy; }
    public String getCrossOriginOpenerPolicy() { return crossOriginOpenerPolicy; }
    public String getCrossOriginResourcePolicy() { return crossOriginResourcePolicy; }
    public boolean isDnsPrefetchControl() { return dnsPrefetchControl; }
    public boolean isDnsPrefetchControlAllow() { return dnsPrefetchControlAllow; }
    public String getFrameguard() { return frameguard; }
    public boolean isHidePoweredBy() { return hidePoweredBy; }
    public boolean isHsts() { return hsts; }
    public int getHstsMaxAge() { return hstsMaxAge; }
    public boolean isHstsIncludeSubDomains() { return hstsIncludeSubDomains; }
    public boolean isHstsPreload() { return hstsPreload; }
    public boolean isIeNoOpen() { return ieNoOpen; }
    public boolean isNoSniff() { return noSniff; }
    public boolean isOriginAgentCluster() { return originAgentCluster; }
    public String getPermittedCrossDomainPolicies(){ return permittedCrossDomainPolicies; }
    public String getReferrerPolicy() { return referrerPolicy; }
    public boolean isXssProtection() { return xssProtection; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean contentSecurityPolicy = true;
        private Map<String, List<String>> cspDirectives = null;
        private boolean crossOriginEmbedderPolicy = true;
        private String crossOriginOpenerPolicy = "same-origin";
        private String crossOriginResourcePolicy = "same-origin";
        private boolean dnsPrefetchControl = true;
        private boolean dnsPrefetchControlAllow = false;
        private String frameguard = "SAMEORIGIN";
        private boolean hidePoweredBy = true;
        private boolean hsts = true;
        private int hstsMaxAge = 15552000;
        private boolean hstsIncludeSubDomains = true;
        private boolean hstsPreload = false;
        private boolean ieNoOpen = true;
        private boolean noSniff = true;
        private boolean originAgentCluster = true;
        private String permittedCrossDomainPolicies = "none";
        private String referrerPolicy = "no-referrer";
        private boolean xssProtection = false;

        public Builder contentSecurityPolicy(boolean v)  { this.contentSecurityPolicy = v; return this; }
        public Builder cspDirectives(Map<String, List<String>> v) { this.cspDirectives = v; return this; }
        public Builder crossOriginEmbedderPolicy(boolean v) { this.crossOriginEmbedderPolicy = v; return this; }
        public Builder crossOriginOpenerPolicy(String v) { this.crossOriginOpenerPolicy = v; return this; }
        public Builder crossOriginResourcePolicy(String v) { this.crossOriginResourcePolicy = v; return this; }
        public Builder dnsPrefetchControl(boolean v) { this.dnsPrefetchControl = v; return this; }
        public Builder dnsPrefetchControlAllow(boolean v) { this.dnsPrefetchControlAllow = v; return this; }
        public Builder frameguard(String v) { this.frameguard = v; return this; }
        public Builder hidePoweredBy(boolean v) { this.hidePoweredBy = v; return this; }
        public Builder hsts(boolean v) { this.hsts = v; return this; }
        public Builder hstsMaxAge(int v) { this.hstsMaxAge = v; return this; }
        public Builder hstsIncludeSubDomains(boolean v)  { this.hstsIncludeSubDomains = v; return this; }
        public Builder hstsPreload(boolean v) { this.hstsPreload = v; return this; }
        public Builder ieNoOpen(boolean v) { this.ieNoOpen = v; return this; }
        public Builder noSniff(boolean v) { this.noSniff = v; return this; }
        public Builder originAgentCluster(boolean v) { this.originAgentCluster = v; return this; }
        public Builder permittedCrossDomainPolicies(String v) { this.permittedCrossDomainPolicies = v; return this; }
        public Builder referrerPolicy(String v) { this.referrerPolicy = v; return this; }
        public Builder xssProtection(boolean v) { this.xssProtection = v; return this; }

        public HelmetOptions build() { return new HelmetOptions(this); }
    }
}
