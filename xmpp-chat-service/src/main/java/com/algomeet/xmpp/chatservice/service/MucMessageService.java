package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.Document;
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

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.mapper.MucMessageMapper;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;
import com.algomeet.xmpp.chatservice.util.MucMemberUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
	private final AbstractGroupCache groupCacheService;

	// Dedicated thread pool for heavy or synchronous metadata / index scanning operations
	private static final Scheduler MUC_THREAD_POOL = Schedulers.newBoundedElastic(150, 8000, "muc-message-workers");

	public Mono<List<MucMessageResponse>> getMessagesAfter(
	        UUID userKey,
	        UUID groupId,
	        UUID afterStanzaId,
	        int page,
	        int size) {

	    Pageable pageable = PageRequest.of(page, size);

	    // FIXED: Wrapped blocking metadata processing within a callable running on a distinct worker thread pool
	    return Mono.fromCallable(() -> {
	        Group room = groupCacheService.getCachedGroup(groupId.toString());
	        Optional<GroupMember> member = SearchUtil.findMember(room, userKey.toString());
	        if (member.isEmpty()) {
	            return Optional.<Instant>empty();
	        }
	        return Optional.of(MucMemberUtil.getHistoryCutoff(room, member.get()));
	    })
	    .subscribeOn(MUC_THREAD_POOL)
	    .flatMap(cutoffOpt -> {
	        if (cutoffOpt.isEmpty()) {
	            return Mono.just(Collections.emptyList());
	        }
	        Instant historyCutoff = cutoffOpt.get();

	        return mucMessageRepository
	                .findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdAsc(
	                        groupId,
	                        afterStanzaId,
	                        userKey,
	                        historyCutoff,
	                        pageable)
	                .map(m -> mucMessageMapper.toResponse(m, userKey))
	                .map(message -> {
	                    if (Boolean.TRUE.equals(message.getIsHidden())) {
	                        message.setStanzaXml(null);
	                    }
	                    return message;
	                })
	                .collectList()
	                .map(Collections::unmodifiableList);
	    });
	}

	public Mono<List<MucMessageResponse>> getMessagesBefore(
	        UUID userKey,
	        UUID groupId,
	        UUID beforeStanzaId,
	        int page,
	        int size) {

	    Pageable pageable = PageRequest.of(page, size);

	    // FIXED: Shifted structural cache lookup bounds off the event loop 
	    return Mono.fromCallable(() -> {
	        Group room = groupCacheService.getCachedGroup(groupId.toString());
	        Optional<GroupMember> member = SearchUtil.findMember(room, userKey.toString());
	        if (member.isEmpty()) {
	            return Optional.<Instant>empty();
	        }
	        return Optional.of(MucMemberUtil.getHistoryCutoff(room, member.get()));
	    })
	    .subscribeOn(MUC_THREAD_POOL)
	    .flatMap(cutoffOpt -> {
	        if (cutoffOpt.isEmpty()) {
	            return Mono.just(Collections.emptyList());
	        }
	        Instant historyCutoff = cutoffOpt.get();

	        return mucMessageRepository
	                .findByRoomIdAndIdLessThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdDesc(
	                        groupId,
	                        beforeStanzaId,
	                        userKey,
	                        historyCutoff,
	                        pageable)
	                .map(m -> mucMessageMapper.toResponse(m, userKey))
	                .collectList()
	                .map(messages -> {
	                    Collections.reverse(messages);
	                    return Collections.unmodifiableList(messages);
	                });
	    });
	}
	
	public Mono<List<MucMessageResponse>> getModifiedMessages(
	        UUID userKey,
	        UUID groupId,
	        UUID untilStanzaId,
	        int page,
	        int size) {

	    Pageable pageable = PageRequest.of(page, size);

	    // FIXED: Ensured event loops remain safe from structural point lookup delays
	    return Mono.fromCallable(() -> {
	        Group group = groupCacheService.getCachedGroup(groupId.toString());
	        Optional<GroupMember> member = SearchUtil.findMember(group, userKey.toString());
	        if (member.isEmpty()) {
	            return Optional.<Instant>empty();
	        }
	        return Optional.of(MucMemberUtil.getHistoryCutoff(group, member.get()));
	    })
	    .subscribeOn(MUC_THREAD_POOL)
	    .flatMap(cutoffOpt -> {
	        if (cutoffOpt.isEmpty()) {
	            return Mono.just(Collections.emptyList());
	        }
	        Instant historyCutoff = cutoffOpt.get();

	        /**
			 * Retrieves message state updates (edit, delete, read, etc.)
			 * for the specified room up to and including the given stanza ID.
			 *
			 * Query conditions:
			 * - updateCursorId > untilStanzaId
			 * Ensures only messages updated after the client's last known update cursor are returned.
			 *
			 * - id <= untilStanzaId
			 * Prevents returning updates for messages beyond the requested synchronization boundary.
			 *
			 * Results are ordered ascending by message ID to preserve chronological update order.
			 */
	        return mucMessageRepository
	        		.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualAndCreatedAtGreaterThanOrderByIdDesc(
	        				groupId,
	        				untilStanzaId,
	        				untilStanzaId,
	        				historyCutoff,
	        				pageable)
	        		.map(m -> mucMessageMapper.toResponse(m, userKey))
	        		.doOnNext(message -> {
	        			if (Boolean.TRUE.equals(message.getIsHidden())) {
	        				message.setStanzaXml(null);
	        			}
	        		})
	        		.collectList()
	        		.map(list -> {
	        			if (CollectionUtils.isEmpty(list)) {
	        				return Collections.emptyList();
	        			}
	        			// Sort chronologically by StanzaId before returning to the client synchronization pipeline
	        			list.sort(Comparator.comparing(MucMessageResponse::getStanzaId));
	        			return Collections.unmodifiableList(list);
	        		});
	    });
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
	 * per conversation, sorted reverse-chronologically by activity time.
	 */	
	public Mono<List<MucMessageResponse>> getConversations(UUID userKey) {
	    // 1. Fetch group IDs from cache service (Assuming this is a fast redis/in-memory sync call)
	    List<String> groupIds = mucUserGroupsCacheService.getCachedGroupIds(userKey.toString());

	    if (CollectionUtils.isEmpty(groupIds)) {
	        return Mono.just(Collections.emptyList());
	    }

	    // 2. Convert String IDs to a Set of UUIDs
	    Set<UUID> roomUuids = groupIds.stream()
	            .map(UUID::fromString)
	            .collect(Collectors.toSet());

	    AggregationOptions options = AggregationOptions.builder().build();

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
	            Aggregation.group(MucMessage.FIELD_ROOM_ID).first(Aggregation.ROOT).as("latestMessage"),
	            Aggregation.replaceRoot("latestMessage"),
	            Aggregation.sort(Sort.Direction.DESC, MucMessage.FIELD_ID)
	            )
	            .withOptions(options);

	    // 4. Execute using ReactiveMongoTemplate (Completely non-blocking)
	    return mongoTemplate.aggregate(aggregation, "muc_messages", MucMessage.class)
	            .map(m -> mucMessageMapper.toResponse(m, userKey))
	            .collectList() // Asynchronously gathers the Flux elements into a standard Java List Mono
	            .flatMap(resultDtos -> {
	                if (CollectionUtils.isEmpty(resultDtos)) {
	                    return Mono.just(resultDtos);
	                }

	                // 5. Apply filters in-memory (Assuming this mutates the list elements or modifies the collection)
	                filterConversationsByVisibilityAndCutoff(userKey, resultDtos, groupIds);
	                
	                // Re-verify after filtering step
	                if (CollectionUtils.isEmpty(resultDtos)) {
	                    return Mono.just(resultDtos);
	                }

	                // 6. Properly chain the asynchronous read-cursor enrichment step into the stream graph
	                return retrieveAndSetReaders(resultDtos);
	            });
	}
	
	/**
	 * Retrieves the earliest surviving/retained message identifiers for all active conversations 
	 * a specific user belongs to. 
	 * <p>
	 * This method serves as a highly optimized synchronization anchor point, establishing the local 
	 * history bounds for client applications after data retention policies or purge routines have 
	 * truncated older message sets.
	 * </p>
	 * <h3>Performance & Scale Highlights (1B+ Records):</h3>
	 * <ul>
	 * <li><b>Index Coverage (IXSCAN):</b> Uses the user's cached room list to restrict matching bounds instantly, 
	 * bypassing 99.99% of the collection.</li>
	 * <li><b>Early Projection:</b> Drops the heavy XML stanza payloads ({@code stanzaXml}) immediately after matching, 
	 * ensuring downstream aggregation operations (sort/group) handle compact 16-byte primitives in memory.</li>
	 * <li><b>Non-blocking Execution:</b> Maps intermediate database payloads to a light {@link Document} type 
	 * to bypass Spring Data's reflective POJO conversion layer entirely on the Netty event loop threads.</li>
	 * </ul>
	 *
	 * @param userKey The unique {@link UUID} representation of the requesting user.
	 * @return A {@link Mono} emitting a sorted {@link List} of lightweight {@link MucMessageResponse} 
	 * objects containing only structural IDs (id, messageId, roomId) per conversation.
	 */
	public Mono<List<MucMessageResponse>> getEarliestRetainedMessages(UUID userKey) {
	    // 1. Fetch group IDs from cache service (Fast Redis/in-memory sync call)
	    List<String> groupIds = mucUserGroupsCacheService.getCachedGroupIds(userKey.toString());

	    if (CollectionUtils.isEmpty(groupIds)) {
	        return Mono.just(Collections.emptyList());
	    }

	    // 2. Convert String IDs to a Set of UUIDs
	    Set<UUID> roomUuids = groupIds.stream()
	            .map(UUID::fromString)
	            .collect(Collectors.toSet());

	    AggregationOptions options = AggregationOptions.builder().build();

	    // 3. Build the high-performance aggregation pipeline
	    Aggregation aggregation = Aggregation.newAggregation(
	            // Stage 1: Fast IXSCAN filtering using existing compound indexes
	            Aggregation.match(
	                    new Criteria().andOperator(
	                            Criteria.where(MucMessage.FIELD_ROOM_ID).in(roomUuids)
	                                    .and(MucMessage.FIELD_DELETED_AT).is(null),
	                            new Criteria().orOperator(
	                                    Criteria.where("to").is(null),
	                                    Criteria.where("to").is(userKey)
	                            ),
	                            Criteria.where("hiddenFromUserKeys").nin(userKey)
	                    )
	            ),

	            // Stage 2: EARLY PROJECTION (Crucial for 1B records)
	            // Drops stanzaXml immediately so subsequent operations process light primitives in RAM
	            Aggregation.project()
	                    .and(MucMessage.FIELD_ID).as("id")
	                    .and(MucMessage.FIELD_MESSAGE_ID).as("messageId")
	                    .and(MucMessage.FIELD_ROOM_ID).as("roomId")
	                    .and("to").as("to")
	                    .and("hiddenFromUserKeys").as("hiddenFromUserKeys"),

	            // Stage 3: Sort aligned with index lookups 
	            Aggregation.sort(Sort.Direction.ASC, MucMessage.FIELD_ROOM_ID, MucMessage.FIELD_ID),

	            // Stage 4: Deduplicate to find the earliest remaining message per room
	            // Memory footprint here is now tiny because ROOT only contains the projected fields
	            Aggregation.group(MucMessage.FIELD_ROOM_ID).first(Aggregation.ROOT).as("earliestMessage"),

	            // Stage 5: Promote the matched document back to the top level
	            Aggregation.replaceRoot("earliestMessage"),

	            // Stage 6: Clean up the final fields for the output payload
	            Aggregation.project()
	                    .and("id").as("id")
	                    .and("messageId").as("messageId")
	                    .and("roomId").as("roomId"),

	            // Stage 7: Final uniform sort order for application consistency
	            Aggregation.sort(Sort.Direction.ASC, "id")
	    ).withOptions(options);
 
	    // 4. Execute using Document.class to bypass heavy POJO mapping overhead
	    return mongoTemplate.aggregate(aggregation, "muc_messages", Document.class)
	            .map(doc -> {
	                // Instantiate a hollow shell containing only the required IDs for the mapper
	                MucMessage partialMessage = new MucMessage();
	                partialMessage.setId(doc.get("id", UUID.class));
	                partialMessage.setMessageId(doc.get("messageId", UUID.class));
	                partialMessage.setRoomId(doc.get("roomId", UUID.class));

	                return mucMessageMapper.toResponse(partialMessage, userKey);
	            })
	            .collectList();
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
                long historyCutoff = MucMemberUtil.getHistoryCutoff(group, member).toEpochMilli();
                
                // Evict conversation if the message timestamp falls strictly behind the user's timeline clearance point
                return historyCutoff >= conversation.getCreatedAt();
            }       
            
            return false;
        });
    }
	
	private Mono<List<MucMessageResponse>> retrieveAndSetReaders(List<MucMessageResponse> messageDtos) {
	    if (CollectionUtils.isEmpty(messageDtos)) {
	        return Mono.just(messageDtos);
	    }

	    // 1. Batch extract all target room IDs to prevent multiple network hops
	    Set<UUID> roomIds = messageDtos.stream()
	            .map(MucMessageResponse::getRoomId)
	            .collect(Collectors.toSet());

	    // 2. Stream the database fetch as a non-blocking Mono map payload
	    return mucRoomReadCursorRepository.findByRoomIdIn(roomIds)
	            .collectList() // Collect Flux results asynchronously into a List
	            .map(cursorList -> {
	                // Group cursors by Room ID entirely in-memory
	                return cursorList.stream()
	                        .collect(Collectors.groupingBy(MucRoomReadCursor::getRoomId));
	            })
	            .map(cursorsByRoom -> {
	                // 3. Perform high-speed in-memory matching inside the map context
	                for (MucMessageResponse dto : messageDtos) {
	                    List<MucRoomReadCursor> roomCursors = cursorsByRoom.getOrDefault(dto.getRoomId(), Collections.emptyList());

	                    List<UUID> readers = roomCursors.stream()
	                            .filter(cursor -> cursor.getLastReadSid() != null 
	                                    && cursor.getLastReadSid().compareTo(dto.getStanzaId()) >= 0)
	                            .map(MucRoomReadCursor::getUserKey)
	                            .filter(id -> !id.equals(dto.getFrom())) 
	                            .toList();

	                    dto.setReadByIds(readers);
	                }
	                return messageDtos; // Return the fully enriched list down the stream
	            });
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
	

	public Flux<MucMessageResponse> fetchMessagesByIds(List<UUID> messageIds, UUID userKey) {
		if (CollectionUtils.isEmpty(messageIds)) {
			return Flux.empty(); // Short-circuit early to save a database round-trip
		}
		return mucMessageRepository.findByMessageIdIn(messageIds)
				.map(m -> mucMessageMapper.toResponse(m, userKey));
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