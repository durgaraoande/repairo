package com.repairo.config;

import com.repairo.ratelimit.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcRateLimitConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;

    @Autowired
    public WebMvcRateLimitConfig(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitProperties.isEnabled() && !rateLimitProperties.getPolicies().isEmpty()) {
            registry.addInterceptor(new RateLimitInterceptor(rateLimitProperties));
        }
    }
}
