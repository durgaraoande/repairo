package com.repairo.ratelimit;

import com.repairo.config.RateLimitProperties;
import com.repairo.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple token-bucket rate limiting interceptor. Suitable for low traffic admin usage.
 * Not distributed (single node local memory). For multi-node, external store required.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    public RateLimitInterceptor(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) return true;

        String uri = request.getRequestURI();
        RateLimitProperties.Policy policy = resolvePolicy(uri);
        if (policy == null) return true; // no policy matches

        boolean diff = "true".equalsIgnoreCase(request.getParameter("diff"));
        int baseCapacity = policy.getCapacity();
        int effectiveCapacity = (diff && policy.getDiffCapacity() != null) ? policy.getDiffCapacity() : baseCapacity;

        String key = buildKey(policy, request, uri, diff);
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(effectiveCapacity, policy.getPeriodMs()));
    bucket.maybeRefill(effectiveCapacity, policy.getPeriodMs());
        if (bucket.tryConsume()) {
            return true;
        }
        long retryMs = bucket.millisUntilRefill(policy.getPeriodMs());
    tooMany(response, effectiveCapacity, policy.getPeriodMs(), retryMs);
        log.debug("Rate limit exceeded for key {} (remaining=0, retryMs={})", key, retryMs);
        return false;
    }

    private RateLimitProperties.Policy resolvePolicy(String uri) {
        for (RateLimitProperties.Policy p : properties.getPolicies().values()) {
            if (p.getPaths() == null) continue;
            for (String pattern : p.getPaths()) {
                if (pathMatcher.match(pattern, uri)) return p;
            }
        }
        return null;
    }

    private String buildKey(RateLimitProperties.Policy policy, HttpServletRequest req, String uri, boolean diff) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri);
        if (diff) sb.append("|diff");
        if (policy.isPerIp()) sb.append('|').append(clientIp(req));
        return sb.toString();
    }

    private String clientIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int comma = h.indexOf(',');
            return comma > 0 ? h.substring(0, comma).trim() : h.trim();
        }
        return Objects.toString(req.getRemoteAddr(), "unknown");
    }

    private void tooMany(HttpServletResponse resp, int capacity, long periodMs, long retryMs) throws IOException {
        resp.setStatus(429);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> api = ApiResponse.error("Rate limit exceeded. Try again in " + (retryMs/1000.0) + "s");
        try {
            resp.getWriter().write(JSON.writeValueAsString(api));
        } catch (JsonProcessingException e) {
            // Fallback minimal JSON
            String msg = api.getError() == null ? "Rate limit exceeded" : api.getError();
            resp.getWriter().write("{\"success\":false,\"error\":\"" + msg.replace("\"", "\\\"") + "\",\"timestamp\":" + api.getTimestamp() + "}");
        }
    }

    /** Token bucket (refill whole capacity linearly over period). */
    private static class Bucket {
        private double tokens;
        private long lastRefillEpochMs;

        Bucket(int capacity, long periodMs) {
            this.tokens = capacity;
            this.lastRefillEpochMs = Instant.now().toEpochMilli();
        }

        synchronized void maybeRefill(int capacity, long periodMs) {
            long now = Instant.now().toEpochMilli();
            long elapsed = now - lastRefillEpochMs;
            if (elapsed <= 0) return;
            double ratePerMs = (double) capacity / (double) periodMs;
            tokens = Math.min(capacity, tokens + elapsed * ratePerMs);
            lastRefillEpochMs = now;
        }

        synchronized boolean tryConsume() {
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized long millisUntilRefill(long periodMs) {
            if (tokens >= 1.0) return 0L;
            // We need 1 token; compute time until next whole token accrues
            // Simplistic: assume linear accrual from last refill timestamp
            // Since we update lastRefill on maybeRefill, remaining fraction determines wait
            // tokens gained per ms = capacity/period, but we don't have capacity here; approximate using last known accrual
            // For simplicity return 500ms fallback
            return 500L;
        }
    }
}
