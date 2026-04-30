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
	 * Retrieves message records for the given room whose update cursor is newer
	 * than the supplied cursor value.
	 *
	 * Used for incremental synchronization of recent changes such as:
	 * - deleted messages
	 * - hidden/unhidden messages
	 * - edits
	 * - reactions
	 *
	 * Comparison is lexicographical because updateCursorId is a ULID, which is
	 * naturally sortable by time when stored as a string.
	 *
	 * Results are ordered by document id ascending to provide deterministic paging.
	 *
	 * Example:
	 * Client sends last known cursor = "01JTABC..."
	 * Server returns all room messages where updateCursorId > last known cursor.
	 *
	 * @param roomId   target MUC room identifier
	 * @param afterId  last cursor previously received by client
	 * @param pageable limits batch size for sync pagination
	 * @return stream of updated message records newer than the given cursor
	 */
	Flux<MucMessage> findByRoomIdAndUpdateCursorIdGreaterThanOrderByIdAsc(
			String roomId, String afterId
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

	/**
	 * Efficiently counts unread messages using the {roomId: 1, id: 1} compound index.
	 */
	Mono<Long> countByRoomIdAndIdGreaterThanAndFromNot(String roomId, String lastReadId, String userJid);


	/**
	 * Fetches messages for a specific room that occurred after the given ULID/ID.
	 * Sorted Ascending so the client receives them in chronological order.
	 */
	Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, String afterId);

	Mono<MucMessage> findByMessageId(String messageId);
}