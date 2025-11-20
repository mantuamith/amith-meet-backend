package com.algomeet.notificationservice.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.algomeet.notificationservice.websocket.handler.AuthHandshakeInterceptor;
import com.algomeet.notificationservice.websocket.handler.AuthInfoHandshakeHandler;
import com.algomeet.notificationservice.websocket.handler.TextWebsocketHandler;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TextWebsocketHandler textWebsocketHandler;    
    private final AuthInfoHandshakeHandler handshakeHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    	registry
    	.addHandler(textWebsocketHandler, "/notifications/subscribe")  
    	.addInterceptors(authHandshakeInterceptor)
    	.setHandshakeHandler(handshakeHandler)    	
    	.setAllowedOriginPatterns("*"); 	    	
    }   
}