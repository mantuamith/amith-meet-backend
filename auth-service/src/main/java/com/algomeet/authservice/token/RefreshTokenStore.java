package com.algomeet.authservice.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

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
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;

    @Value("${auth.refresh-token.ttl-days:7}")
    private long refreshTokenTtlDays;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";
    private static final String USER_KEY_PREFIX  = "RTE:";

    /** Save a refresh token and bind it to a user (email). */
    public void save(String token, String email) {
        String tokenToEmailKey = REFRESH_TOKEN_PREFIX + token;
        String emailToTokensKey = USER_KEY_PREFIX + email;

        // Save token -> email with TTL
        redisTemplate.opsForValue().set(tokenToEmailKey, email, refreshTokenTtlDays, TimeUnit.DAYS);
        
        // Add token to the user's set of active tokens
        redisTemplate.opsForSet().add(emailToTokensKey, token);
        
        // Optional: Set TTL on the user set to eventually clean up abandoned sets
        redisTemplate.expire(emailToTokensKey, refreshTokenTtlDays, TimeUnit.DAYS);
        
        log.debug("Saved refresh token for user={}", email);
    }
    
    /** Does this refresh token exist? */
    public boolean exists(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_TOKEN_PREFIX + token));
    }

    /** Remove a single refresh token (e.g., on logout). */
    public void remove(String token) {
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String email = redisTemplate.opsForValue().get(tokenKey);

        if (email != null) {
            redisTemplate.delete(tokenKey);
            redisTemplate.opsForSet().remove(USER_KEY_PREFIX + email, token);
            log.debug("Removed refresh token for user={}", email);
        }
    }

    /** Remove all refresh tokens for a given email. */
    public void revokeAllForUser(String email) {
        String emailToTokensKey = USER_KEY_PREFIX + email;
        Set<String> tokens = redisTemplate.opsForSet().members(emailToTokensKey);

        if (tokens != null && !tokens.isEmpty()) {
            // Remove each individual token key
            for (String token : tokens) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + token);
            }
            // Remove the user's token set
            redisTemplate.delete(emailToTokensKey);
            log.info("Revoked {} refresh token(s) for user={}", tokens.size(), email);
        }
    }

    /** Legacy alias. */
    public void clearAllForEmail(String email) {
        revokeAllForUser(email);
    }

    /** Look up the user email for a given refresh token. */
    public String getEmailForToken(String token) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + token);
    }
}