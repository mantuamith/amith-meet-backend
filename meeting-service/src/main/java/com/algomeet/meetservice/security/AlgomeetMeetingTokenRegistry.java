// com.algomeet.meetservice.security.AlgomeetMeetingTokenRegistry
package com.algomeet.meetservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AlgomeetMeetingTokenRegistry {

    private final StringRedisTemplate redis;

    private String key(String meetingId, String userKey) {
        return "algomeet:mtg:" + meetingId + ":user:" + userKey;
    }

    public Optional<String> getIfActive(String meetingId, String userKey) {
        return Optional.ofNullable(redis.opsForValue().get(key(meetingId, userKey)));
    }

    public void save(String meetingId, String userKey, String token, Duration ttl) {
        redis.opsForValue().set(key(meetingId, userKey), token, ttl);
    }

    public void revoke(String meetingId, String userKey) {
        redis.delete(key(meetingId, userKey));
    }
}
