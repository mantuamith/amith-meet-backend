// auth-service
package com.algomeet.authservice.session;

import com.algomeet.authservice.client.UserClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SidCache {

    private final UserClient userClient;

    // email -> (sid, expiresAt)
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    // keep short; we want near-real-time enforcement
    private static final long TTL_SECONDS = 20;

    public String getCurrentSid(String email) {
        Entry e = cache.get(email);
        Instant now = Instant.now();
        if (e != null && e.expiresAt().isAfter(now)) {
            return e.sid();
        }
        // fetch fresh
        String sid = null;
        try {
            var resp = userClient.getActiveSession(email);
            if (resp != null) sid = (String) resp.get("sid");
        } catch (Exception ignored) {
            // If user-service is down, we’ll fail SAFE below (treat as mismatch => revoke)
        }
        Instant exp = now.plusSeconds(TTL_SECONDS);
        cache.put(email, new Entry(sid, exp));
        return sid;
    }

    public void invalidate(String email) {
        cache.remove(email);
    }

    private record Entry(String sid, Instant expiresAt) {}
}
