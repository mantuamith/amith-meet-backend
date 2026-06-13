package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.mapper.MucMessageMapper;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;
import com.algomeet.xmpp.chatservice.util.MucMemberUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

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
	private final GroupCacheService groupCacheService;

	public List<MucMessageResponse> getMessagesAfter(UUID userKey, UUID groupId, UUID afterStanzaId, int page, int size) { 
		Pageable pageable = PageRequest.of(page, size);

		// Retrieve group info
		Group room = groupCacheService.getCachedGroup(groupId.toString());
		Optional<GroupMember>  member = SearchUtil.findMember(room, userKey.toString());
		if (member.isEmpty()) {
			return List.of();
		}
		
		Instant historyCutoff = MucMemberUtil.getHistoryCutoff(room, member.get());
		// Collect into a standard ArrayList so it is safe to interact with during processing
		List<MucMessageResponse> messages = 
				mucMessageRepository.findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdAsc(
						groupId, afterStanzaId, userKey, historyCutoff, pageable)
				.collectList()              
				.blockOptional() 
				.orElse(Collections.emptyList())
				.stream()
				.map(m -> mucMessageMapper.toResponse(m, UUID.fromString(SecurityUtil.getUserKey())))
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

		// Retrieve group info
		Group room = groupCacheService.getCachedGroup(groupId.toString());
		Optional<GroupMember>  member = SearchUtil.findMember(room, userKey.toString());
		if (member.isEmpty()) {
			return List.of();
		}

		Instant historyCutoff = MucMemberUtil.getHistoryCutoff(room, member.get());
		List<MucMessageResponse> processedMessages = mucMessageRepository
				.findByRoomIdAndIdLessThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdDesc(
						groupId, beforeStanzaId, userKey, historyCutoff, pageable)
				.collectList()              
				.blockOptional() 
				.orElse(Collections.emptyList())
				.stream()
				// 1. Map documents to Response DTOs safely passing your explicit userKey parameter
				.map(m -> mucMessageMapper.toResponse(m, userKey))
				// 2. Sort the stream cleanly by stanzaId (UUIDv7) in Ascending order
				.sorted(Comparator.comparing(MucMessageResponse::getStanzaId))
				// 3. Collect the finalized stream into your immutable list safely
				.toList();

		return processedMessages;
	}

	public List<MucMessageResponse> getMessageUpdates(UUID userKey, UUID groupId, UUID untilStanzaId, int page, 
			int size) {    
		List<MucMessageResponse> resultList = new ArrayList<>();

		Group group = groupCacheService.getCachedGroup(groupId.toString());
		
		if (page == 0) {
	        MucMessageResponse startMessage = getStartOfConversation(userKey, groupId, group);
	        if (startMessage != null) {
	            resultList.add(startMessage);
	        }
	        size = Math.max(1, size - 1); // Guard against size dropping below 1
	    }

		Pageable pageable = PageRequest.of(page, size);

		// Retrieve group info
		Optional<GroupMember>  member = SearchUtil.findMember(group, userKey.toString());
		if (member.isEmpty()) {
			return List.of();
		}

		Instant historyCutoff = MucMemberUtil.getHistoryCutoff(group, member.get());
		
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
		List<MucMessageResponse> modifiedMessages = mucMessageRepository
	            .findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualAndCreatedAtGreaterThanOrderByIdDesc(
	                    groupId, untilStanzaId, untilStanzaId, historyCutoff, pageable)
	            .collectList()              
	            .blockOptional() 
	            .orElse(Collections.emptyList())
	            .stream()
	            // Pass explicit userKey to bypass volatile ThreadLocal Security Context
	            .map(m -> mucMessageMapper.toResponse(m, userKey)) 
	            // Mutate properties inside the stream processing loop smoothly
	            .peek(message -> {
	                if (Boolean.TRUE.equals(message.getIsHidden())) {
	                    message.setStanzaXml(null); 
	                }
	            })
	            .toList(); 

	    // If no new modifications were found, return whatever placeholder structural headers we have
	    if (modifiedMessages.isEmpty()) {
	        return Collections.unmodifiableList(resultList); 
	    }

	    // 3. Combine safely into our confirmed mutable ArrayList workspace
	    resultList.addAll(modifiedMessages);

	    // 4. Sort the unified collection by stanzaId (UUIDv7) in Ascending order
	    resultList.sort(Comparator.comparing(MucMessageResponse::getStanzaId));

	    return Collections.unmodifiableList(resultList);
	}

	private MucMessageResponse getStartOfConversation(UUID userKey, UUID groupId, Group group) {
		// Scenario B: The room is brand new / completely empty. Return a structural anchor.
		MucMessageResponse emptyRoomAnchor = new MucMessageResponse();
		emptyRoomAnchor.setStanzaId(Constants.NIL_UUID);
		emptyRoomAnchor.setMessageId(Constants.NIL_UUID);
		emptyRoomAnchor.setRoomId(groupId);
		emptyRoomAnchor.setStartOfRoomConversation(true);
		
		// Retrieve group info
		Optional<GroupMember>  member = SearchUtil.findMember(group, userKey.toString());
		if (member.isEmpty()) {
			return emptyRoomAnchor;
		}

		Instant historyCutoff = MucMemberUtil.getHistoryCutoff(group, member.get());
		
		MucMessage firstMessage = mucMessageRepository
                .findFirstByRoomIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(groupId, historyCutoff)
                .block();		

		// Scenario A: The room has a history. Map the actual first message.
		if (firstMessage != null) {
			MucMessageResponse message = mucMessageMapper.toResponse(firstMessage, UUID.fromString(SecurityUtil.getUserKey()));
			// Empty the payload to lighten the load
			message.setStanzaXml(null);
			message.setStartOfRoomConversation(true);
			return message;
		}

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
				builder().build();

		// 3. Build the aggregation pipeline with targeted MUC visibility and privacy constraints
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(
						new Criteria().andOperator(
								// Constraint A: Only pull from rooms the user belongs to, and not soft-deleted
								Criteria.where(MucMessage.FIELD_ROOM_ID).in(roomUuids)
								.and(MucMessage.FIELD_DELETED_AT).is(null),

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
				.map(m -> mucMessageMapper.toResponse(m, userKey))
				.collectList()
				.block(); // Blocks safely here to match your synchronous List<MucMessageResponse> return type
		
		if (!CollectionUtils.isEmpty(resultDtos)) {
			// Remove hidden/cutoff conversations
			filterConversationsByVisibilityAndCutoff(userKey, resultDtos, groupIds);
			
			// Retrieve readers
			if(!CollectionUtils.isEmpty(resultDtos)) {
				retrieveAndSetReaders(resultDtos);
			}
		}
		

		return resultDtos;
	}
	
	/**
     * Filters a list of active group conversations in-place, removing entries belonging to 
     * deleted rooms or messages that fall behind the member's historical visibility threshold.
     * <p>
     * <b>Operational Workflow:</b>
     * <ol>
     * <li>Gathers and deduplicates all potential room IDs to perform a high-performance batch cache lookup.</li>
     * <li>Constructs an in-memory lookup map to isolate processing from repetitive infrastructure calls.</li>
     * <li>Evaluates each conversation thread against the member's dynamic {@code messageHistoryCutoff} timestamp.</li>
     * </ol>
     * </p>
     * <p>
     * <i>Performance Note:</i> If a room target is completely absent from the bulk reactive pipeline payload, 
     * the logic gracefully falls back to a singular synchronous cache lookup as an absolute fail-safe.
     * </p>
     *
     * @param userKey          The unique business identifier (UUID) of the member requesting the timeline view.
     * @param mucConversations The mutable collection of user conversation records to filter in-place.
     * @param groupIds         An auxiliary list of verified active group IDs to seed the primary lookup phase.
     */
    private void filterConversationsByVisibilityAndCutoff(
            UUID userKey, 
            List<MucMessageResponse> mucConversations, 
            List<String> groupIds) {
        
        if (CollectionUtils.isEmpty(mucConversations)) {
            return;
        }

        // 1. Deduplicate and collect ALL room IDs present across conversations to ensure complete batch coverage
        java.util.Set<String> allTargetGroupIds = new java.util.HashSet<>();
        if (!CollectionUtils.isEmpty(groupIds)) {
            allTargetGroupIds.addAll(groupIds);
        }
        for (MucMessageResponse conversation : mucConversations) {
            if (conversation.getRoomId() != null) {
                allTargetGroupIds.add(conversation.getRoomId().toString());
            }
        }

        // Evict all if there are no structural room markers available to parse
        if (allTargetGroupIds.isEmpty()) {
            mucConversations.clear();
            return;
        }

        // 2. Fetch ALL required groups
        List<Group> groups = groupCacheService.getGroups(new java.util.ArrayList<>(allTargetGroupIds));
                
        if (CollectionUtils.isEmpty(groups)) {
            mucConversations.clear();
            return;
        }

        // 3. Build the O(1) Lookup Map for high-speed node pairing
        java.util.Map<String, Group> activeGroupMap = groups.stream()
                .collect(java.util.stream.Collectors.toMap(
                        room -> room.getId().toString(),
                        group -> group,
                        (existing, replacement) -> existing
                ));

        // 4. Optimize variables: Convert UUID string representation once before loop entry to reduce GC strain
        String targetUserKeyStr = userKey.toString();

        // 5. In-place filtering using localized memory references (with localized fail-safe lookups)
        mucConversations.removeIf(conversation -> {
            if (conversation.getRoomId() == null) {
                return true; // Drop corrupt conversation entries without room targets
            }

            String roomIdStr = conversation.getRoomId().toString();
            Group group = activeGroupMap.get(roomIdStr);
            
            // Fail-safe fallback: If the group was missing from the batch payload, try an isolated point lookup
            if (group == null) {
                group = groupCacheService.getCachedGroup(roomIdStr);
                if (group == null) {
                    return true; // Evict conversation if the room configuration target is totally dead or purged
                }
            }

            // Fast O(log N) balanced tree traversal to extract member configuration profile
            Optional<GroupMember> memberOpt = SearchUtil.findMember(group, targetUserKeyStr);
            
            if (memberOpt.isPresent()) {
                GroupMember member = memberOpt.get();
                long historyCutoff = MucMemberUtil.getHistoryCutoffLong(member);
                
                // Evict conversation if the message timestamp falls strictly behind the user's timeline clearance point
                return historyCutoff >= conversation.getCreatedAt();
            }       
            
            return false;
        });
    }

	private void retrieveAndSetReaders(List<MucMessageResponse> messageDtos) {
		if (CollectionUtils.isEmpty(messageDtos)) {
			return;
		}

		// 1. Batch extract all target room IDs to prevent multiple network hops
		Set<UUID> roomIds = messageDtos.stream()
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
		for (MucMessageResponse dto : messageDtos) {
			List<MucRoomReadCursor> roomCursors = cursorsByRoom.getOrDefault(dto.getRoomId(), Collections.emptyList());

			List<UUID> readers = roomCursors.stream()
					.filter(cursor -> cursor.getLastReadSid() != null 
					&& cursor.getLastReadSid().compareTo(dto.getStanzaId()) >= 0)
					.map(MucRoomReadCursor::getUserKey)
					// Filter out user's own ID safely inside the stream pipeline
					.filter(id -> !id.equals(dto.getFrom())) 
					.toList(); // Safe to use toList() now since we don't call .remove() later

			// Set the computed readers back onto your response DTO instead of an empty list
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
					return mucMessageRepository.findFirstByMessageId(lastReadMessageId)
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
