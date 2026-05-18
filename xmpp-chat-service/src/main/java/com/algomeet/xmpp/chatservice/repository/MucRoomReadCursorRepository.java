package com.algomeet.xmpp.chatservice.repository;

import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MucRoomReadCursorRepository extends ReactiveMongoRepository<MucRoomReadCursor, String> {
	/**
     * Retrieves all active room read cursors for a specific user.
     * 
     * <p>Used primarily when loading the user's main chat/roster list to 
     * compute unread badge counts across all their channels simultaneously.</p>
     *
     * @param userKey The unique identifier of the user.
     * @return A {@link Flux} emitting the user's room read cursors.
     */
    Flux<MucRoomReadCursor> findByUserKey(String userKey);
    
    /**
     * Retrieves the single, authoritative read cursor for a specific user within a specific room.
     * 
     * <p>This operation is a high-speed point lookup ($O(1)$ complexity) that utilizes the 
     * {@code idx_cursors_user_room} compound index prefix path to locate a user's chronological 
     * read anchor instantly without scanning documents.</p>
     *
     * @param userKey The unique identifier of the user whose cursor is being retrieved.
     * @param roomId  The target group chat room identifier.
     * @return A {@link Mono} emitting the matching {@link MucRoomReadCursor}, 
     *         or completing empty if the user has no recorded read history in this room.
     */
    Mono<MucRoomReadCursor> findByUserKeyAndRoomId(String userKey, String roomId);
    
    /**
     * Finds all room read cursors in the specified room where the
     * last read message ID is greater than equal the provided message ID.
     *
     * <p>
     * UUIDv7 message IDs are lexicographically sortable, allowing
     * chronological range queries directly on the message ID field.
     * </p>
     *
     * @param roomId the room identifier
     * @param messageId the reference message ID threshold
     * @return a reactive stream of matching room read cursors
     */
    Flux<MucRoomReadCursor> findByRoomIdAndLastReadMidGreaterThanEqual(String roomId, String lastReadMid);
}