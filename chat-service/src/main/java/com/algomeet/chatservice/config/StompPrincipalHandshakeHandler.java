
package com.algomeet.chatservice.config;

import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class StompPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                      org.springframework.web.socket.WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Principal p = (Principal) attributes.get("principal");
        return p != null ? p : super.determineUser(request, wsHandler, attributes);
    }
}
