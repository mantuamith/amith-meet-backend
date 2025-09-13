package com.algomeet.contactservice.config;

public final class AuthCtx {
    private AuthCtx(){}
    public static java.util.UUID userKeyFrom(org.springframework.security.core.Authentication a) {
        if (a == null) return null;
        Object d = a.getDetails();
        if (d instanceof java.util.Map<?,?> m) {
            Object v = m.get("user_key");
            if (v instanceof String s && !s.isBlank()) {
                try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }
}