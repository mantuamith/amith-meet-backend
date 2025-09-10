package com.algomeet.chatservice.config;

import java.security.Principal;

public record StompUserPrincipal(String userKey, String username, String email) implements Principal {
    @Override
    public String getName() {
        return username;
    }
}
