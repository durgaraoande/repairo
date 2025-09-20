package com.repairo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "app.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${app.websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${app.websocket.heartbeat.inbound-ms:10000}")
    private long inboundHeartbeat;

    @Value("${app.websocket.heartbeat.outbound-ms:10000}")
    private long outboundHeartbeat;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{outboundHeartbeat, inboundHeartbeat})
              .setTaskScheduler(websocketTaskScheduler());
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
        log.info("WebSocket STOMP broker configured: /topic, /queue with heartbeats out:{}ms in:{}ms", outboundHeartbeat, inboundHeartbeat);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = allowedOrigins.split("[,\n ]+");
        registry.addEndpoint("/ws").setAllowedOriginPatterns(patterns).withSockJS();
        registry.addEndpoint("/ws-native").setAllowedOriginPatterns(patterns);
        log.info("WebSocket STOMP endpoints registered at /ws (SockJS) and /ws-native; allowed origins: {}", (Object)patterns);
    }

    @Bean
    public TaskScheduler websocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}