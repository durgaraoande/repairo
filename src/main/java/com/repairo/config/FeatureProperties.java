package com.repairo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureProperties {
    private boolean websockets = true; // alias of app.websocket.enabled (kept for future consolidation)
    private boolean diffPollingDefaultEnabled = true; // auto switch to diff after first full load
    private boolean strictJson = false; // future: reject legacy/plain
    private boolean auditStatus = true; // toggle audit entity persistence + ws publish

    public boolean isWebsockets() { return websockets; }
    public void setWebsockets(boolean websockets) { this.websockets = websockets; }
    public boolean isDiffPollingDefaultEnabled() { return diffPollingDefaultEnabled; }
    public void setDiffPollingDefaultEnabled(boolean diffPollingDefaultEnabled) { this.diffPollingDefaultEnabled = diffPollingDefaultEnabled; }
    public boolean isStrictJson() { return strictJson; }
    public void setStrictJson(boolean strictJson) { this.strictJson = strictJson; }
    public boolean isAuditStatus() { return auditStatus; }
    public void setAuditStatus(boolean auditStatus) { this.auditStatus = auditStatus; }
}
