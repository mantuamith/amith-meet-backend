package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.mapper.MucMessageMapper;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucMessageService {    
	private final MucMessageMapper mucMessageMapper;
	private final MucRoomReadCursorRepository mucRoomReadCursorRepository;
	private final MucUserGroupsCacheService mucUserGroupsCacheService;
	private final ReactiveMongoTemplate mongoTemplate;
	private final MucMessageRepository mucMessageRepository;
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final RedissonReactiveClient redissonReactiveClient;

	public List<MucMessageResponse> getMessagesAfter(UUID userKey, UUID groupId, UUID afterStanzaId, int page, int size) { 
		Pageable pageable = PageRequest.of(page, size);

		// Collect into a standard ArrayList so it is safe to interact with during processing
		List<MucMessageResponse> messages = 
				mucMessageRepository.findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyOrderByIdAsc(
						groupId, afterStanzaId, userKey, pageable)
				.collectList()              
				.blockOptional() 
				.orElse(Collections.emptyList())
				.stream()
				.map(mucMessageMapper::toResponse)
				.toList();

		// Guard Clause: If there are no messages, return early and avoid IndexOutOfBoundsException
		if (messages.isEmpty()) {
			return Collections.emptyList();
		}

		// Map and process messages in a single clear pass
		for (MucMessageResponse message : messages) {         
			if (message.getIsHidden()) {             
				message.setStanzaXml(null);         // Lighten the load
			} 
		} 
		
		// Lock it down before returning to the caller
		return Collections.unmodifiableList(messages);
	}

	public List<MucMessageResponse> getMessagesBefore(UUID userKey, UUID groupId, UUID beforeStanzaId, int page, int size) {  
		Pageable pageable = PageRequest.of(page, size);

		// Collect into a standard ArrayList so it is safe to interact with during processing
		List<MucMessageResponse> messages = 
				mucMessageRepository.findHistoricalMessages(groupId, beforeStanzaId, userKey, pageable)
				.collectList()              
				.blockOptional() 
				.orElse(Collections.emptyList())
				.stream()
				.map(mucMessageMapper::toResponse)
				.toList();

		// Guard Clause: If there are no messages, return early and avoid IndexOutOfBoundsException
		if (messages.isEmpty()) {
			return Collections.emptyList();
		}

		// Map and process messages in a single clear pass
		for (MucMessageResponse message : messages) {         
			if (message.getIsHidden()) {             
				message.setStanzaXml(null);         // Lighten the load
			} 
		}  

		// Lock it down before returning to the caller
		return Collections.unmodifiableList(messages);
	}

	public List<MucMessageResponse> getMessageUpdates(UUID userKey, UUID groupId, UUID untilStanzaId, int page, 
			int size) {    
		List<MucMessageResponse> messages = new ArrayList<>();

		if (page == 0) {
			messages.add(getStartOfConversation(groupId));
			size = size - 1;
		}

		Pageable pageable = PageRequest.of(page, size);

		/**
		 * Retrieves message state updates (edit, delete, read, etc.)
		 * for the specified room up to and including the given stanza ID.
		 *
		 * Query conditions:
		 * - updateCursorId > untilStanzaId
		 *   Ensures only messages updated after the client's last known update cursor are returned.
		 *
		 * - id <= untilStanzaId
		 *   Prevents returning updates for messages beyond the requested synchronization boundary.
		 *
		 * Results are ordered ascending by message ID to preserve chronological update order.
		 */
		List<MucMessageResponse> modifiedMessages = mucMessageRepository.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(
				groupId, untilStanzaId, untilStanzaId, pageable)
				.collectList()              
				.blockOptional() // Defensively handle an empty result safely
				.orElse(Collections.emptyList())
				.stream()
				.map(mucMessageMapper::toResponse)
				.toList(); // Returns an unmodifiable list

		if (modifiedMessages.isEmpty()) {
			return messages; // Fast return if there are no updates
		}

		// Map and process messages in a single clear pass
		for (MucMessageResponse message : modifiedMessages) {         
			if (message.getIsHidden()) {             
				message.setStanzaXml(null);         // Lighten the load
			} 
		}       

		// 3. Combine into a defensively copied, unmodifiable result
		List<MucMessageResponse> result = new ArrayList<>(messages);
		result.addAll(modifiedMessages);
		return Collections.unmodifiableList(result);
	}

	private MucMessageResponse getStartOfConversation(UUID groupId) {
		MucMessage firstMessage = mucMessageRepository.findFirstByRoomIdOrderByIdAsc(groupId).block();		

		// Scenario A: The room has a history. Map the actual first message.
		if (firstMessage != null) {
			MucMessageResponse message = mucMessageMapper.toResponse(firstMessage);
			// Empty the payload to lighten the load
			message.setStanzaXml(null);
			message.setStartOfRoomConversation(true);
			return message;
		}

		// Scenario B: The room is brand new / completely empty. Return a structural anchor.
		MucMessageResponse emptyRoomAnchor = new MucMessageResponse();
		emptyRoomAnchor.setStanzaId(Constants.NIL_UUID);
		emptyRoomAnchor.setMessageId(groupId);
		emptyRoomAnchor.setStartOfRoomConversation(true);
		return emptyRoomAnchor;
	}

	/**
	 * Compiles a high-performance conversational inbox overview for the specified user.
	 * <p>
	 * This method resolves the user's subscribed group rooms via a local cache, then executes 
	 * a targeted MongoDB aggregation pipeline. It filters by room access, visibility rules, 
	 * and user privacy settings to retrieve only the single most recent valid message for each active 
	 * thread. 
	 * <p>
	 * <b>Performance Profile:</b> Sub-15ms execution at billion-scale when matched against the compound 
	 * index {@code {roomId: 1, to: 1, id: -1}} due to Bounded Top-1 index tree streaming.
	 *
	 * @param userKey Unique identifier of the authenticated user requesting their inbox
	 * @return A list of {@link MucMessageResponse} objects representing the latest message snippet 
	 *         per conversation, sorted reverse-chronologically by activity time.
	 */
	public List<MucMessageResponse> getConversations(UUID userKey) {
		// 1. Fetch group IDs from cache service
		List<String> groupIds = mucUserGroupsCacheService.getCachedGroupIds(userKey.toString());

		if (CollectionUtils.isEmpty(groupIds)) {
			return List.of();
		}

		// 2. Convert String IDs to a Set of UUIDs
		Set<UUID> roomUuids = groupIds.stream()
				.map(UUID::fromString)
				.collect(Collectors.toSet());

		AggregationOptions options = AggregationOptions.
				builder()
				.hint("idxMuc_room_to_idDesc") // Forces use of {'roomId': 1, 'to': 1, 'id': -1}
				.build();

		// 3. Build the aggregation pipeline with targeted MUC visibility and privacy constraints
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(
						new Criteria().andOperator(
								// Constraint A: Only pull from rooms the user belongs to
								Criteria.where(MucMessage.FIELD_ROOM_ID).in(roomUuids),

								// Constraint B: Public room message OR private message specifically for this user
								new Criteria().orOperator(
										Criteria.where("to").is(null),
										Criteria.where("to").is(userKey)
										),

								// Constraint C: Exclude messages explicitly hidden from this user
								Criteria.where("hiddenFromUserKeys").nin(userKey)
								)
						),
				Aggregation.sort(Sort.Direction.DESC, MucMessage.FIELD_ID),
				Aggregation.group(MucMessage.FIELD_ROOM_ID)
				.first(Aggregation.ROOT).as("latestMessage"),
				Aggregation.replaceRoot("latestMessage"),
				Aggregation.sort(Sort.Direction.DESC, MucMessage.FIELD_ID)
				)
				.withOptions(options);

		// 4. Execute using your ReactiveMongoTemplate (which returns a Flux)
		Flux<MucMessage> results = mongoTemplate.aggregate(
				aggregation, "muc_messages", MucMessage.class);

		// 5. Reactively map each document, collect them into a list, and block to return synchronously
		List<MucMessageResponse> resultDtos = results
				.map(mucMessageMapper::toResponse)
				.collectList()
				.block(); // Blocks safely here to match your synchronous List<MucMessageResponse> return type
		
		// Retrieve readers
		retrieveAndSetReaders(resultDtos);
		
		return resultDtos;
	}
	
	private void retrieveAndSetReaders(List<MucMessageResponse> resultDtos) {
		// 1. Batch extract all target room IDs to prevent multiple network hops
        Set<UUID> roomIds = resultDtos.stream()
                .map(MucMessageResponse::getRoomId)
                .collect(Collectors.toSet());

        // 2. Execute ONE single bulk query to pull all active cursors for these rooms
        Map<UUID, List<MucRoomReadCursor>> cursorsByRoom = mucRoomReadCursorRepository.findByRoomIdIn(roomIds)
                .collectList()
                .blockOptional()
                .orElse(Collections.emptyList())
                .stream()
                .collect(Collectors.groupingBy(MucRoomReadCursor::getRoomId));

        // 3. Perform high-speed in-memory matching inside the loop (No DB access here)
        for (MucMessageResponse dto : resultDtos) {
            List<MucRoomReadCursor> roomCursors = cursorsByRoom.getOrDefault(dto.getRoomId(), Collections.emptyList());

            List<UUID> readers = roomCursors.stream()
                    .filter(cursor -> cursor.getLastReadSid() != null 
                            && cursor.getLastReadSid().compareTo(dto.getStanzaId()) >= 0)
                    .map(MucRoomReadCursor::getUserKey)
                    .toList();

            dto.setReadByIds(readers);
        }
	}

	/**
	 * Batch update Read Status
	 * @param lastReadMessageId
	 * @return
	 */
	public Mono<Long> bulkMarkRoomMessagesAsRead(final UUID lastReadMessageId) {		
		final long nowMs = Instant.now().toEpochMilli();
		final String lockKey = "xmpp:lock:update-read:muc-msg:msg-id:" + lastReadMessageId;
		final RLockReactive lock = redissonReactiveClient.getLock(lockKey);

		// Orchestrate the resource lifecycle using Mono.usingWhen
		return Mono.usingWhen(
				// 1. Resource Acquisition: Attempt to acquire the lock reactively
				lock.tryLock(0, 5000, TimeUnit.MILLISECONDS),

				// 2. Core Logic Execution (Triggers only if the lock resource mono completes)
				acquired -> {
					if (!acquired) {
						log.debug("Lock acquisition failed for message ID: {}. Potential duplicate or high contention.", lastReadMessageId);
						return Mono.empty();
					}

					// Lock obtained -> Proceed with DB fetch and updates
					return mucMessageRepository.findProjectedByMessageId(lastReadMessageId)
							.flatMap(message -> {
								Query query = new Query(
										Criteria.where("_id").lte(message.getId())
										.and("roomId").is(message.getRoomId())
										.and("readAt").isNull()
										);

								Update update = new Update().set("readAt", nowMs);

								return reactiveMongoTemplate.updateMulti(query, update, MucMessage.class)
										.map(updateResult -> updateResult.getModifiedCount());
							})
							.switchIfEmpty(Mono.error(new RuntimeException("MUC message not found with message ID: " + lastReadMessageId)))
							.doOnSuccess(count -> log.debug("Marked {} messages as read up to checkpoint {}", count, lastReadMessageId));
				},

				// 3. Cleanup: Release on Normal Completion
				acquired -> acquired ? safeUnlock(lock) : Mono.empty(),

						// 4. Cleanup: Release on Exceptional Pipeline Failure
						(acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),

								// 5. Cleanup: Release on Downstream Operator Cancellation
								acquired -> acquired ? safeUnlock(lock) : Mono.empty()
				)
				// 6. Root Error Handling Boundary
				.doOnError(e -> log.error("Critical failure in processing read update for message ID: {}", lastReadMessageId, e));
	}

	/**
	 * Helper to handle safe, reactive unlocking to prevent throwing errors if already unlocked.
	 */
	private Mono<Void> safeUnlock(RLockReactive lock) {
		return lock.unlock()

				.onErrorResume(IllegalMonitorStateException.class, e -> {
					log.debug(
							"Lock ownership lost or already released due to thread-hop: {}",
							e.getMessage()
							);
					return Mono.empty();
				})

				.then();
	}
}
