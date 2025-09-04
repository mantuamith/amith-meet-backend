package com.algomeet.authservice.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory refresh token store (dev/test).
 *
 * Thread-safe, process-local. Tokens are lost on app restart.
 *
 * ===========================
 * REDIS MIGRATION (step-by-step)
 * ===========================
 * 1) Add deps:
 *    - org.springframework.boot:spring-boot-starter-data-redis
 *    - (default client is Lettuce)
 *
 * 2) Configure application.yml:
 *    spring:
 *      data:
 *        redis:
 *          host: localhost
 *          port: 6379
 *          password: <if any>
 *          ssl: false
 *
 * 3) Provide a bean:
 *    - Use StringRedisTemplate (string keys/values) to avoid serializer surprises.
 *
 * 4) Key design:
 *    - RT:<token> -> <email>            (String; set EX <ttlSeconds> == refresh token lifetime)
 *    - RTE:<email> -> Set(<token,...>)  (Set; optional TTL >= max refresh lifetime)
 *
 * 5) Save(token,email) [atomic]:
 *    - MULTI
 *      SET RT:<token> <email> EX <ttlSeconds>
 *      SADD RTE:<email> <token>
 *      EXEC
 *    (or a single Lua script for atomicity)
 *
 * 6) remove(token):
 *    - email = GET RT:<token>
 *    - DEL RT:<token>
 *    - if email != null -> SREM RTE:<email> <token>
 *
 * 7) revokeAllForUser(email):
 *    - tokens = SMEMBERS RTE:<email>
 *    - DEL RT:<t> for each token
 *    - DEL RTE:<email>
 *
 * 8) exists(token): EXISTS RT:<token>
 * 9) getEmailForToken(token): GET RT:<token>
 *
 * 10) Wire it:
 *    - Implement a Redis-backed class with the SAME method signatures as this class.
 *    - Use @Profile("redis") on the Redis impl and @Profile("!redis") (or default) on this in-memory one,
 *      or use @ConditionalOnProperty (e.g., auth.tokens.store=redis|memory).
 *
 * 11) TTLs:
 *    - Set EX on RT:<token> so refresh tokens expire automatically in Redis.
 *    - Optionally set TTL on RTE:<email> to the longest token lifetime you allow.
 *
 * 12) Tests:
 *    - Concurrency test for save/remove/revokeAllForUser.
 *    - TTL expiry test confirms GET returns null after expiry.
 */
@Slf4j
@Component
public class RefreshTokenStore {

    // refreshToken -> email
    private final Map<String, String> tokenToEmail = new ConcurrentHashMap<>();

    // email -> set of refreshTokens
    private final Map<String, Set<String>> emailToTokens = new ConcurrentHashMap<>();

    /** Save a refresh token and bind it to a user (email). */
    public void save(String token, String email) {
        tokenToEmail.put(token, email);
        emailToTokens
                .computeIfAbsent(email, k -> ConcurrentHashMap.newKeySet())
                .add(token);
    }

    /** Does this refresh token exist? */
    public boolean exists(String token) {
        return tokenToEmail.containsKey(token);
    }

    /** Remove a single refresh token (e.g., on logout). */
    public void remove(String token) {
        String email = tokenToEmail.remove(token);
        if (email != null) {
            Set<String> tokens = emailToTokens.get(email);
            if (tokens != null) {
                tokens.remove(token);
                if (tokens.isEmpty()) {
                    emailToTokens.remove(email);
                }
            }
        }
    }

    /** Remove all refresh tokens for a given email (used when overriding sessions). */
    public void revokeAllForUser(String email) {
        Set<String> tokens = emailToTokens.remove(email);
        int count = 0;
        if (tokens != null) {
            for (String t : tokens) {
                if (tokenToEmail.remove(t) != null) count++;
            }
        }
        log.info("Revoked {} refresh token(s) for user={}", count, email);
    }

    /** Legacy alias if other spots still call this name. */
    public void clearAllForEmail(String email) {
        revokeAllForUser(email);
    }

    /** Look up the user email for a given refresh token (used in /logout). */
    public String getEmailForToken(String token) {
        return tokenToEmail.get(token);
    }
}
