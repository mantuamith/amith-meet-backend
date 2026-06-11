package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.util.XmppSyncStanzaComposer;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import static com.algomeet.xmpp.chatservice.document.UnreadCount.*;

@Slf4j
@Service
@AllArgsConstructor
public class UnreadCountService {
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final DomainProperties domainProperties;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageRepository offlineMessageRepository;

	/**
	 * Non-blocking increment of the unread count.
	 */
	public Mono<UnreadCount> incrementUnreadCount(String senderKey, String recipientKey) {
		String id = getConversationId(senderKey, recipientKey);

		Query query = new Query(Criteria.where("_id").is(id));
		Update update = new Update()
				.inc(UNREAD_COUNT, 1)
				.set(USER_KEY, UUID.fromString(recipientKey))
				.set(SENDER_KEY, UUID.fromString(senderKey))
				.set(LAST_INCREMENT_AT, Instant.now().toEpochMilli());

		// upsert returns the updated document
		return reactiveMongoTemplate.upsert(query, update, UnreadCount.class)
				.then(reactiveMongoTemplate.findById(id, UnreadCount.class));
	}

	/**
	 * Decrements the unread message count by 1 for a specific conversation channel, 
	 * provided the current count is strictly greater than 0.
	 * <p>
	 * This method executes an atomic server-side increment/decrement operation directly 
	 * within MongoDB. By using a conditional query ({@code unreadCount > 0}), it prevents 
	 * negative counter invariants (underflows) that could otherwise be caused by concurrent 
	 * or duplicate client read receipts.
	 * </p>
	 *
	 * @param senderKey    The unique identifier/key of the user who sent the messages.
	 * @param recipientKey The unique identifier/key of the user who received the messages (owner of the unread count).
	 * @param messageId    The UUID (typically UUIDv7) representing the checkpoint up to which messages have been read.
	 * @param principal    The authenticated XMPP principal executing this operation.
	 * @return A {@code Mono<UnreadCount>} emitting the updated document state after the decrement, 
	 *         or the existing state if no decrement occurred.
	 */
	public Mono<UnreadCount> decrementUnreadCount(String senderKey, String recipientKey, UUID messageId, XmppPrincipal principal) {
		// Generate the unique composite document ID for this specific sender-recipient boundary
		String id = getConversationId(senderKey, recipientKey);

		// Construct an atomic guard query: only match and update if the counter is currently > 0
		Query query = new Query(Criteria.where("_id").is(id).and(UNREAD_COUNT).gt(0));

		// Apply server-side isolation: decrement the value by 1 and log the timestamp
		Update update = new Update().inc(UNREAD_COUNT, -1)
				.set(LAST_DECREMENT_AT, Instant.now().toEpochMilli())
				.set(LAST_READ_MID, messageId);

		// 1. Execute the conditional update first
		return reactiveMongoTemplate.updateFirst(query, update, UnreadCount.class)
				// 2. Once the update completes, pull the fresh/unmodified document state back
				.then(reactiveMongoTemplate.findById(id, UnreadCount.class))
				// 3. Return the resolved document within the reactive pipeline stream
				.flatMap(unreadCount -> {
					return Mono.just(unreadCount);
				});
	}

	/**
	 * Decrements or synchronizes the unread message count for a specific chat conversation.
	 * <p>
	 * This method employs an <b>Optimistic Locking pattern</b> paired with a reactive retry 
	 * mechanism to safely update counts in a highly concurrent environment (e.g., handling 
	 * multiple active XMPP stanzas or simultaneous device syncs). 
	 * </p>
	 * <p>
	 * <b>Workflow:</b>
	 * <ol>
	 *   <li>Fetches the current baseline {@code UnreadCount} to capture the {@code lastIncrementAt} timestamp.</li>
	 *   <li>Queries the absolute remaining pending messages from the {@code offlineMessageRepository}.</li>
	 *   <li>Executes an atomic conditional update matching the document ID and the captured timestamp.</li>
	 *   <li>If a race condition occurs (e.g., a new message arrives mid-flight and modifies the timestamp), 
	 *       the update fails to modify any document, throwing a {@link ConcurrentModificationException}.</li>
	 *   <li>The operation intercepts this conflict and uses {@code Mono.defer()} to pull fresh state and retry up to 3 times.</li>
	 * </ol>
	 * </p>
	 *
	 * @param senderKey    The unique identifier/key of the user who sent the messages.
	 * @param recipientKey The unique identifier/key of the user who received the messages (owner of the unread count).
	 * @param messageId    The UUID (typically UUIDv7) representing the checkpoint up to which messages have been read.
	 * @param principal    The authenticated XMPP principal executing this operation.
	 * @return A {@code Mono<UnreadCount>} emitting the fully updated and refreshed document state upon successful execution.
	 * @throws ConcurrentModificationException if the unread count state remains unstable after 3 retry attempts due to heavy concurrent writes.
	 */
	public Mono<UnreadCount> syncUnreadCount(UUID senderKey, UUID recipientKey, UUID messageId) {
	    String id = getConversationId(senderKey.toString(), recipientKey.toString());

	    // 1. Explicitly type Mono.<UnreadCount>defer so the compiler knows the final target type
	    return Mono.<UnreadCount>defer(() -> 
	        reactiveMongoTemplate.findById(id, UnreadCount.class)
	            .flatMap(currentUnread -> {
	                Long capturedIncrementAt = currentUnread.getLastIncrementAt();
	                
	                // 2. Fetch the message first
	                return offlineMessageRepository.findOfflineMessageViewByMessageId(messageId)
	                    .flatMap(message -> {
	                        UUID stanzaId = message.getStanzaId();

	                        // 3. Fetch the count, explicitly telling flatMap it will evaluate to an UnreadCount
	                        return offlineMessageRepository.countByToAndFromAndStanzaIdGreaterThanAndCountableTrue(
	                                recipientKey, senderKey, stanzaId)
	                            .flatMap((Long count) -> { // Explicit lambda param type helps inference

	                                Query query = new Query(
	                                        Criteria.where("_id").is(id)
	                                        .and(LAST_INCREMENT_AT).is(capturedIncrementAt)
	                                        );

	                                Update update = new Update()
	                                        .set(UNREAD_COUNT, count)
	                                        .set(LAST_DECREMENT_AT, Instant.now().toEpochMilli())
	                                        .set(LAST_READ_MID, messageId)
	                                        .set(LAST_READ_SID, stanzaId);

	                                // 4. Perform the conditional update
	                                return reactiveMongoTemplate.updateFirst(query, update, UnreadCount.class)
	                                        .flatMap(updateResult -> {
	                                            if (updateResult.getModifiedCount() == 0) {
	                                                return Mono.error(new ConcurrentModificationException(
	                                                        "Unread count changed during processing. Retrying..."
	                                                        ));
	                                            }
	                                            // Final return matching the Mono<UnreadCount> expectation
	                                            return reactiveMongoTemplate.findById(id, UnreadCount.class);
	                                        });
	                            });
	                    });
	            })
	    )
	    .retryWhen(Retry.max(3).filter(throwable -> throwable instanceof ConcurrentModificationException));
	}
	
	public Mono<UnreadCount> syncUnreadCountByStanzaId(UUID senderKey, UUID recipientKey, UUID messageId, UUID stanzaId) {
	    String id = getConversationId(senderKey.toString(), recipientKey.toString());

	    // 1. Explicitly type Mono.<UnreadCount>defer so the compiler knows the final target type
	    return Mono.<UnreadCount>defer(() -> 
	        reactiveMongoTemplate.findById(id, UnreadCount.class)
	            .flatMap(currentUnread -> {
	                Long capturedIncrementAt = currentUnread.getLastIncrementAt();

	                        // 2. Fetch the count, explicitly telling flatMap it will evaluate to an UnreadCount
	                        return offlineMessageRepository.countByToAndFromAndStanzaIdGreaterThanAndCountableTrue(
	                                recipientKey, senderKey, stanzaId)
	                            .flatMap((Long count) -> { // Explicit lambda param type helps inference

	                                Query query = new Query(
	                                        Criteria.where("_id").is(id)
	                                        .and(LAST_INCREMENT_AT).is(capturedIncrementAt)
	                                        );

	                                Update update = new Update()
	                                        .set(UNREAD_COUNT, count)
	                                        .set(LAST_DECREMENT_AT, Instant.now().toEpochMilli())
	                                        .set(LAST_READ_MID, messageId)
	                                        .set(LAST_READ_SID, stanzaId);

	                                // 3. Perform the conditional update
	                                return reactiveMongoTemplate.updateFirst(query, update, UnreadCount.class)
	                                        .flatMap(updateResult -> {
	                                            if (updateResult.getModifiedCount() == 0) {
	                                                return Mono.error(new ConcurrentModificationException(
	                                                        "Unread count changed during processing. Retrying..."
	                                                        ));
	                                            }
	                                            // Final return matching the Mono<UnreadCount> expectation
	                                            return reactiveMongoTemplate.findById(id, UnreadCount.class);
	                                        });
	                            });

	            })
	    )
	    .retryWhen(Retry.max(3).filter(throwable -> throwable instanceof ConcurrentModificationException));
	}
	
	@Deprecated
	public Mono<Void> resetUnreadCount(UUID senderKey, UUID recipientKey, UUID messageId) {
	    // 1. Trigger the underlying unread sync mutation logic
		return syncUnreadCount(senderKey, recipientKey, messageId)
				.then(Mono.defer(() -> {
	            /**
	             * <message from='.algomeet.app'
	             *          type='headline'>
	             *     <sync xmlns='urn:xmpp:algomeet:sync:history'>
	             *         <conversation peer-key='userKey'
	             *                       cleared-until-message-id='xxxxxx' />
	             *     </sync>
	             * </message>
	             */

	            String payload = XmppSyncStanzaComposer.createDirectClearanceStanza(
	            		domainProperties.getDomain(),
	                    senderKey.toString(), 
	                    messageId.toString()
	            );

	            // 2. Generate unique tracking identifier for cluster delivery routing
	            String clusterMessageId = UuidCreator.getTimeOrderedEpoch().toString();
	            
	            // 3. Dispatch the timeline clearance payload to secondary user devices
	            clusterMessagePublisher.convertAndSendToUser(
	                    clusterMessageId,
	                    recipientKey.toString(), 
	                    recipientKey.toString(), 
	                    ChatType.CHAT, 
	                    payload
	            );
	            
	            return Mono.empty(); // Satisfies the lazy transformation contract
				}));
	}

	/**
	 * Aggregates total unread count for a user across all senders reactively.
	 */
	public Mono<Integer> getTotalUnreadForUser(String userKey) {
		Query query = new Query(Criteria.where(USER_KEY).is(UUID.fromString(userKey)));

		return reactiveMongoTemplate.find(query, UnreadCount.class)
				.map(UnreadCount::getUnreadCount)
				.reduce(0, Integer::sum);
	}

	/**
	 * Retrieves a list of all unread counts for a specific recipient.
	 * Usually used to populate the main chat list/inbox.
	 */
	public Flux<UnreadCount> getUnreadCountsForUser(String recipientKey) {
		Query query = new Query(Criteria.where(USER_KEY).is(UUID.fromString(recipientKey))
				.and(UNREAD_COUNT).gt(0));

		return reactiveMongoTemplate.find(query, UnreadCount.class);
	}

	/**
	 * Get unread count for a specific sender-recipient relationship.
	 */
	public Mono<Integer> getUnreadCount(String senderKey, String recipientKey) {
		String id = getConversationId(senderKey, recipientKey);

		return reactiveMongoTemplate.findById(id, UnreadCount.class)
				.map(UnreadCount::getUnreadCount)
				.defaultIfEmpty(0);
	}    

	/**
	 * Retrieves a paginated list of distinct database row IDs for interactions involving the target user.
	 * * @param targetUserKey The user key to filter by.
	 * @param page          The page number (starting from 0).
	 * @param size          The number of records per page.
	 * @return A Flux of document _ids, sorted by most recent activity, limited by the page size.
	 */
	public Flux<String> getRecentContactKeysReactive(String targetUserKey, int page, int size) {
		long skipValues = (long) page * size;

		// A standard query using $or CAN use indexes for sorting if structured right,
		// but requires a single unified index tracking the sorting field across the query.
		Query query = new Query(
				new Criteria().orOperator(
						Criteria.where(USER_KEY).is(UUID.fromString(targetUserKey)),
						Criteria.where(SENDER_KEY).is(UUID.fromString(targetUserKey))
						)
				).with(Sort.by(Sort.Direction.DESC, LAST_INCREMENT_AT))
				.skip(skipValues)
				.limit(size);

		return reactiveMongoTemplate.find(query, UnreadCount.class)
				.map(UnreadCount::getId)
				.distinct(); 
	}
	
	/**
    * Generates a deterministic conversation identifier from two keys.
    * 
    * @param senderKey The UUID of the sender
    * @param recipientKey The UUID of the recipient
    * @return A formatted String "senderKey_recipientKey"
    */
   public static String getConversationId(String senderKey, String recipientKey) {
       if (senderKey == null || recipientKey == null) {
           throw new IllegalArgumentException("Sender and recipient keys must not be null");
       }
       return String.join("_", senderKey, recipientKey);
   }
}