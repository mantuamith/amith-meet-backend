package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCountUtil;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@Service
@AllArgsConstructor
public class UnreadCountService {
    private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;
    private final ClusterMessagePublisher clusterMessagePublisher;

    /**
     * Non-blocking increment of the unread count.
     */
    public Mono<UnreadCount> incrementUnreadCount(String senderKey, String recipientKey) {
        String id = String.format("%s_%s", senderKey, recipientKey);
        
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update()
                .inc("unread_count", 1)
                .set("user_key", recipientKey)
                .set("sender_key", senderKey)
                .set("updated_at", Instant.now().toEpochMilli());

        // upsert returns the updated document
        return reactiveMongoTemplate.upsert(query, update, UnreadCount.class)
                .then(reactiveMongoTemplate.findById(id, UnreadCount.class));
    }
    
    /**
     * Non-blocking decrement of the unread count.
     * Ensures the count does not drop below zero.
     */
    public Mono<UnreadCount> decrementUnreadCount(String senderKey, String recipientKey) {
        String id = String.format("%s_%s", senderKey, recipientKey);
        
        // Use a query that only decrements if the current count is greater than 0
        Query query = new Query(Criteria.where("_id").is(id).and("unread_count").gt(0));
        Update update = new Update().inc("unread_count", -1);

        return reactiveMongoTemplate.updateFirst(query, update, UnreadCount.class)
                .then(reactiveMongoTemplate.findById(id, UnreadCount.class))
                .flatMap(unreadCount -> {
                    // Sync the new count across other user sessions
                    if (userSessionRegistry.getSessions(recipientKey).size() > 1) {
                        String payload = MucCountUtil.composeCountSync(
                            domainProperties.getDomain(), 
                            jidUtil.getBareJid(recipientKey), 
                            senderKey, 
                            unreadCount.getUnreadCount()
                        );
                        clusterMessagePublisher.convertAndSendToUser(id, recipientKey, recipientKey, ChatType.CHAT, payload);
                    }
                    return Mono.just(unreadCount);
                });
    }

    /**
     * Non-blocking reset of the unread count.
     */
    public Mono<Void> resetUnreadCount(String senderKey, String recipientKey) {
        String id = String.format("%s_%s", senderKey, recipientKey);
        
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("unread_count", 0);
        
        // Publish message to other devices to sync the unread message counts
        /*
        <message from='algomeet.com' to='recipient@algomeet.com' type='headline'>
		  <sync xmlns='urn:xmpp:algomeet:sync:unread'>
		    <direct sender_key='user_abc_123' unread_count='0' />
		  </sync>
		</message> */
        if(userSessionRegistry.getSessions(recipientKey).size() > 1) {
			// Send it user has more that one session for synchronization
			String payload = MucCountUtil.composeCountSync(domainProperties.getDomain(), jidUtil.getBareJid(recipientKey), senderKey, 0);
			clusterMessagePublisher.convertAndSendToUser(id, recipientKey, recipientKey, ChatType.CHAT, payload);
		}

        return reactiveMongoTemplate.updateFirst(query, update, UnreadCount.class)
                .then(); // Returns Mono<Void> to signal completion
    }
    
    /**
     * Aggregates total unread count for a user across all senders reactively.
     */
    public Mono<Integer> getTotalUnreadForUser(String userKey) {
        Query query = new Query(Criteria.where("user_key").is(userKey));
        
        return reactiveMongoTemplate.find(query, UnreadCount.class)
                .map(UnreadCount::getUnreadCount)
                .reduce(0, Integer::sum);
    }
    
    /**
     * Retrieves a list of all unread counts for a specific recipient.
     * Usually used to populate the main chat list/inbox.
     */
    public Flux<UnreadCount> getUnreadCountsForUser(String recipientKey) {
        Query query = new Query(Criteria.where("user_key").is(recipientKey)
                                     .and("unread_count").gt(0));
        
        return reactiveMongoTemplate.find(query, UnreadCount.class);
    }
    
    /**
     * Get unread count for a specific sender-recipient relationship.
     */
    public Mono<Integer> getUnreadCount(String senderKey, String recipientKey) {
        String id = String.format("%s_%s", senderKey, recipientKey);
        
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

        Aggregation aggregation = Aggregation.newAggregation(
            // 1. USE SNAKE_CASE: Match the actual column names in MongoDB
            Aggregation.match(new Criteria().orOperator(
                Criteria.where("user_key").is(targetUserKey),
                Criteria.where("sender_key").is(targetUserKey)
            )),
            
            // 2. Group by _id (which is a string in your DB)
            // Note: use "updated_at" here as well
            Aggregation.group("_id")
                .max("updated_at").as("lastInteraction"),

            // 3. Sort by the alias we created in the Group stage
            Aggregation.sort(Sort.Direction.DESC, "lastInteraction"),

            Aggregation.skip(skipValues),
            Aggregation.limit(size)
        );

        return reactiveMongoTemplate.aggregate(aggregation, "unread_counts", Map.class)
                .map(m -> m.get("_id").toString()); 
    }
}