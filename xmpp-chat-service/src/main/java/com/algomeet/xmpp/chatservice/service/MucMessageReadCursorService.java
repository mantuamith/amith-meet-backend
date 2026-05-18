package com.algomeet.xmpp.chatservice.service;

import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucMessageReadCursorService {

    private final MucMessageRepository mucMessageRepository;
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    /**
     * Computes the unread badge count for a user in a room on demand.
     * 
     * <p>Instead of hitting an easily driftable and fragile counter table, this fetches 
     * the user's read cursor, then executes an index-covered count against the message timeline. 
     * Runs in 1-5ms even across massive datasets.</p>
     *
     * @param userKey The unique key of the user checking their badges.
     * @param roomId  The target chat room identifier.
     * @return A {@link Mono} emitting the exact unread message count. Defaults to 0 if no history exists.
     */
    public Mono<Long> getUnreadCount(final String userKey, final String roomId) {
        final String cursorId = String.format("%s_%s", userKey, roomId);

        return reactiveMongoTemplate.findById(cursorId, MucRoomReadCursor.class)
                .flatMap(cursor -> mucMessageRepository.countUnreadMessages(
                        roomId, 
                        cursor.getLastReadMid(), 
                        userKey
                ))
                // Cold fallback: If the user has never read a single message in this room before,
                // we calculate unread messages starting from the absolute beginning ("") of time.
                .switchIfEmpty(Mono.defer(() -> mucMessageRepository.countUnreadMessages(roomId, "", userKey)))
                .doOnError(e -> log.error("Failed to compute on-demand unread count for user {} in room {}", userKey, roomId, e));
    }

    /**
     * Advances a user's read cursor to a newly consumed message anchor.
     * 
     * <p>This acts as the replacement for the legacy decrement functionality. It is completely 
     * atomic, executing an O(1) upsert that acts as the single source of truth for the user's view state.</p>
     *
     * @param userKey     The user advancing their read timeline.
     * @param roomId      The target chat room identifier.
     * @param lastReadMid The message ID (ULID) up to which the user has read.
     * @return A {@link Mono} emitting the updated {@link MucRoomReadCursor} state.
     */
    public Mono<MucRoomReadCursor> advanceReadCursor(final String userKey, final String roomId, final String lastReadMid) {
        final String cursorId = String.format("%s_%s", userKey, roomId);
        final long nowMs = Instant.now().toEpochMilli();

        final Query query = new Query(Criteria.where("_id").is(cursorId));
        final Update update = new Update()
                .set("userKey", userKey)
                .set("roomId", roomId)
                .set("lastReadMid", lastReadMid)
                .set("lastReadAt", nowMs);

        final FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        return reactiveMongoTemplate.findAndModify(query, update, options, MucRoomReadCursor.class)
                .doOnSuccess(cursor -> log.debug("Advanced read cursor for user {} in room {} to message {}", userKey, roomId, lastReadMid))
                .doOnError(e -> log.error("Failed to advance read cursor for user {} in room {}", userKey, roomId, e));
    }
}