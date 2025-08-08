package com.algomeet.authservice.token;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RefreshTokenStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    public void save(String token, String email) {
        store.put(token, email);
    }

    public boolean exists(String token) {
        return store.containsKey(token);
    }

    public void remove(String token) {
        store.remove(token);
    }

    public void clearAllForEmail(String email) {
        store.entrySet().removeIf(entry -> email.equals(entry.getValue()));
    }

    public String getEmailForToken(String token) {
        return store.get(token);
    }
}
