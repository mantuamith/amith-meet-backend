package com.algomeet.contactservice.config;

import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.UUID;

public final class AuthCtx {
    private AuthCtx(){}
    public static java.util.UUID userKeyFrom(Authentication a) {
        if (a == null) return null;
        Object d = a.getDetails();
        if (d instanceof Map<?,?> m) {
            Object v = m.get("user_key");
            if (v instanceof String s && !s.isBlank()) {
                try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }
}