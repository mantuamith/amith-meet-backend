package com.algomeet.userservice.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class PersonalRoomIdAllocator {

    private static final int SEQ_WIDTH = 10; // 10 digits: 0000000001..9999999999
    private final StringRedisTemplate redis;

    public PersonalRoomIdAllocator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 12-digit numeric Personal Room ID: [2-digit tenant shard][10-digit per-tenant counter]. */
    public String allocateForTenant(String tenantId) {
        String shard = computeTenantShard2(tenantId);        // "00".."99"
        String key   = "prid:seq:" + tenantId;               // per-tenant atomic counter
        Long seq = redis.opsForValue().increment(key);
        if (seq == null || seq <= 0) {
            throw new IllegalStateException("Redis INCR failed for key=" + key);
        }
        if (seq > 9_999_999_999L) {
            throw new IllegalStateException("Per-tenant PRID capacity exceeded");
        }
        return shard + String.format("%0" + SEQ_WIDTH + "d", seq); // 12 digits total
    }

    /** Deterministic 2-digit shard from SHA-256(tenantId) % 100. */
    private String computeTenantShard2(String tenantId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(tenantId.getBytes(StandardCharsets.UTF_8));
            int v = ByteBuffer.wrap(new byte[]{0, d[0], d[1], d[2]}).getInt() & 0x7FFFFFFF;
            int shard = v % 100; // 0..99
            return String.format("%02d", shard);
        } catch (Exception e) {
            int shard = Math.abs(tenantId.hashCode()) % 100;
            return String.format("%02d", shard);
        }
    }
}
