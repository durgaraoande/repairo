package com.repairo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Enable/disable all rate limiting */
    private boolean enabled = true;

    /** Policies keyed by an id (e.g. check-messages) */
    private Map<String, Policy> policies = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Policy> getPolicies() { return policies; }
    public void setPolicies(Map<String, Policy> policies) { this.policies = policies; }

    public static class Policy {
        /** Maximum tokens (capacity) */
        private int capacity = 30;
        /** Refill period in milliseconds (full refill over this window) */
        private long periodMs = 60_000;
        /** One or more Ant-style path patterns this policy applies to */
        private List<String> paths = new ArrayList<>();
        /** Whether IP should be included in the key (true) or global (false). */
        private boolean perIp = true;
        /** Optional specific capacity when diff=true query param present (for check-new-messages). */
        private Integer diffCapacity;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public long getPeriodMs() { return periodMs; }
        public void setPeriodMs(long periodMs) { this.periodMs = periodMs; }
        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
        public boolean isPerIp() { return perIp; }
        public void setPerIp(boolean perIp) { this.perIp = perIp; }
        public Integer getDiffCapacity() { return diffCapacity; }
        public void setDiffCapacity(Integer diffCapacity) { this.diffCapacity = diffCapacity; }
    }
}
