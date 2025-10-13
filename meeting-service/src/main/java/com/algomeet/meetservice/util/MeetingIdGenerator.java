package com.algomeet.meetservice.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Component
public class MeetingIdGenerator {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final long MAX_PER_DAY = 999_999L; // 6 digits

    private final StringRedisTemplate redis;
    private final ZoneId zoneId;
    private final String keyPrefix;
    private final int ttlDays;

    public MeetingIdGenerator(
            StringRedisTemplate stringRedisTemplate,
            ZoneId idGenZoneId, // provided by RedisConfig; defaults to UTC
            @Value("${algomeet.idgen.key-prefix:meet:id:seq:}") String keyPrefix,
            @Value("${algomeet.idgen.ttl-days:10}") int ttlDays
    ) {
        this.redis = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
        this.zoneId = Objects.requireNonNull(idGenZoneId, "idGenZoneId must not be null");
        this.keyPrefix = keyPrefix;
        this.ttlDays = ttlDays;
    }

    /** Returns a 12-digit ID: YYMMDD (in configured ZoneId) + 6-digit counter. */
    public String nextId() {
        // Use system clock; with zone pinned to UTC (or configured), rollover is consistent across nodes.
        Instant now = Instant.now();
        String date = ZonedDateTime.ofInstant(now, zoneId).format(YYMMDD);

        String key = keyPrefix + date;

        Long seq = redis.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException("Redis INCR returned null");
        }

        // Best-effort TTL on first creation of the daily key
        if (seq == 1L && ttlDays > 0) {
            redis.expire(key, Duration.ofDays(ttlDays));
        }

        if (seq > MAX_PER_DAY) {
            throw new IllegalStateException("Daily ID capacity exceeded (> " + MAX_PER_DAY + ")");
        }

        return date + String.format("%06d", seq);
    }
}
