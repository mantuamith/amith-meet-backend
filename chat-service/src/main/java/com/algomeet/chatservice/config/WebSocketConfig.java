package com.algomeet.chatservice.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue","/unread");
        config.setApplicationDestinationPrefixes("/app"); // "/app/chat"
        config.setUserDestinationPrefix("/user"); // for 1-to-1
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat-ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(new StompPrincipalHandshakeHandler())
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
