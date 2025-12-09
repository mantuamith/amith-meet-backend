package com.algomeet.meetservice.util;

import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
public class MeetingRoomIdAllocator {

    private static final int SEQ_WIDTH = 10; // 10 digits -> total 12 with 2-digit shard
    private final StringRedisTemplate redis;

    public MeetingRoomIdAllocator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Allocate a new 12-digit Meeting Room ID and return a Room entity pre-populated.
     * ID format: [2-digit tenant shard][10-digit per-tenant counter]
     */
    public Room allocateForTenant(String tenantId) {
        String shard = computeTenantShard2(tenantId);     // "00".."99"
        String key   = "mtg:seq:" + tenantId;             // per-tenant counter namespace for MEETING rooms
        Long seq = redis.opsForValue().increment(key);
        if (seq == null || seq <= 0) {
            throw new IllegalStateException("Redis INCR failed for key=" + key);
        }
        if (seq > 9_999_999_999L) {
            throw new IllegalStateException("Per-tenant meeting capacity exceeded");
        }

        String roomId = shard + String.format("%0" + SEQ_WIDTH + "d", seq); // 12 digits

        // Build a Room entity; persistence (save) should be handled by the caller/service.
        return Room.builder()
                .roomId(roomId)
                .roomType(RoomType.ADHOC)
                .tenantId(tenantId)
                // ownerUserId / ownerEmail unknown at allocation time -> leave null
                .lobbyDefault(false)
                .recordingDefault(false)
                .createdAt(Instant.now())
                .build();
    }

    /** Same shard computation used in user-service PersonalRoomIdAllocator. */
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
