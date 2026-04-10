package com.algomeet.xmpp.chatservice.service;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCountUtil;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class UnreadCountService {
    private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;

    /**
     * Non-blocking increment of the unread count.
     */
    public Mono<UnreadCount> incrementUnreadCount(String senderKey, String recipientKey) {
        String id = String.format("%s_%s", senderKey, recipientKey);
        
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update()
                .inc("unread_count", 1)
                .set("user_key", recipientKey)
                .set("sender_key", senderKey);

        // upsert returns the updated document
        return reactiveMongoTemplate.upsert(query, update, UnreadCount.class)
                .then(reactiveMongoTemplate.findById(id, UnreadCount.class));
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
			MucCountUtil.composeCountSync(domainProperties.getDomain(), jidUtil.getBareJid(recipientKey), senderKey, 0);
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
}