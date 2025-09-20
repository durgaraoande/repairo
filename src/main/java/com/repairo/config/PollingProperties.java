package com.repairo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.polling")
public class PollingProperties {
    private Messages messages = new Messages();
    private Dashboard dashboard = new Dashboard();

    public Messages getMessages() { return messages; }
    public Dashboard getDashboard() { return dashboard; }

    public static class Messages {
        private long intervalMs = 4000;
        private long maxIntervalMs = 30000;
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public long getMaxIntervalMs() { return maxIntervalMs; }
        public void setMaxIntervalMs(long maxIntervalMs) { this.maxIntervalMs = maxIntervalMs; }
    }
    public static class Dashboard {
        private long intervalMs = 10000;
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }
}
