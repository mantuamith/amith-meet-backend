package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.algomeet.xmpp.chatservice.document.MucMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MucMessageRepository extends ReactiveMongoRepository<MucMessage, String> {

	/**
	 * Retrieve a page of messages for a room starting AFTER a specific sequential ID.
	 * Ideal for infinite scroll / MAM 'after' queries.
	 * 
	 * Version with a limit to satisfy MAM 'max' requests (XEP-0059)
	 */
	@Query("{ 'roomId': ?0, 'id': { $gt: ?1 }, $or: [ { 'to': null }, { 'to': ?2 } ] }")
	Flux<MucMessage> findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyOrderByIdAsc(
			String roomId, String afterId, String userKey, Pageable pageable
			);

	/**
     * Performs a range-based synchronization query for Multi-User Chat (MUC) messages.
     * 
     * <p>This method implements a synchronized "catch-up" mechanism, allowing clients to 
     * retrieve updates that occurred after a specific state (represented by the cursor) 
     * up to a specific snapshot point (the limit ID).</p>
     * 
     * <b>Query Logic:</b>
     * <ul>
     *   <li>{@code roomId}: Equality match for the specific chat room.</li>
     *   <li>{@code updateCursorId}: Greater-than filter to find records modified/created 
     *       after the client's last sync point.</li>
     *   <li>{@code id}: Less-than-or-equal filter to ensure a deterministic result set 
     *       and prevent "sliding window" issues where new messages arrive during the query.</li>
     * </ul>
     * 
     * <b>Performance Note:</b>
     * This method relies on an ESR-compliant (Equality, Sort, Range) compound index:
     * {@code { "roomId": 1, "updateCursorId": 1, "id": 1 }}.
     *
     * @param roomId               The unique identifier of the MUC room.
     * @param afterUpdateCursorId  The cursor ID from the last successful sync; only 
     *                             messages with a higher cursor will be returned.
     * @param limitId              The upper bound message ID (usually the current 
     *                             max ID known to the server) to cap the result set.
     * @return A {@link Flux} of {@link MucMessage} sorted chronologically by their primary ID.
     */
    Flux<MucMessage> findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(
            String roomId, 
            String afterUpdateCursorId, 
            String limitId
    );

	/**
	 * Retrieves older messages (scrolling up).
	 * Maps to MAM 'before' logic: get 'max' messages where ID < beforeId.
	 * If beforeId is null/empty, you get the most recent messages.
	 * If beforeId is provided, you get the page preceding that ID.
	 */
	@Query(value = "{ 'roomId': ?0, 'id': { $lt: ?1 }, $or: [ { 'to': null }, { 'to': ?2 } ] }", 
			sort = "{ 'id': -1 }")
	Flux<MucMessage> findHistoricalMessages(
			String roomId, 
			String beforeId, 
			String userKey, 
			Pageable pageable
			);

	// For the very first load (no cursor)
	@Query(value = "{ 'roomId': ?0, $or: [ { 'to': null }, { 'to': ?1 } ] }", 
		       sort = "{ 'id': -1 }")
	Flux<MucMessage> findByRoomIdOrderByIdDesc(String roomId, String userKey, Pageable pageable);

	Mono<MucMessage> findByMessageId(String messageId);
	
	/**
	 * Retrieves the current first available group message in the conversation.
	 * Used as the synchronization reference for local device conversations,
	 * especially after previous messages have been removed or permanently deleted.
	 *
	 * @param roomId
	 */
	Mono<MucMessage> findFirstByRoomIdOrderByIdAsc(String roomId);
	
	/**
     * Counts unread messages by isolating the room and checking the ID timeline first,
     * before filtering private stanzas using the user key.
     * 
     * <p>At scale, this structure allows MongoDB to slice the timeline window first, 
     * avoiding scanning private messages outside of the unread range.</p>
     *
     * @param roomId            The unique identifier of the MUC room.
     * @param lastReadMessageId The chronological anchor (ULID) where the user left off.
     * @param userKey           The target user key used to filter private messages.
     * @return A {@link Mono} emitting the count of unread messages.
     */
    @Query(value = "{" +
                   "  '$and': [" +
                   "    { 'roomId': ?0 }," +
                   "    { 'messageId': { '$gt': ?1 } }," +
                   "    { '$or': [ { 'to': null }, { 'to': ?2 } ] }" +
                   "  ]" +
                   "}", count = true)
    Mono<Long> countUnreadMessages(String roomId, String lastReadMessageId, String userKey);
}