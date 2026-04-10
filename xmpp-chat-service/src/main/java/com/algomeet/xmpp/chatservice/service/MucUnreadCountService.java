package com.algomeet.xmpp.chatservice.service;

import java.util.List;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.MucUnreadCount;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCountUtil;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class MucUnreadCountService {
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final UserSessionRegistry userSessionRegistry;

	/**
	 * Increments the unread count for a specific user in a specific room.
	 * Uses upsert to ensure the document exists.
	 */
	public Mono<Void> incrementUnreadCount(String userKey, Long roomId) {
		String id = String.format("%s_%d", userKey, roomId);

		Query query = new Query(Criteria.where("_id").is(id));
		Update update = new Update()
				.inc("unread_count", 1)
				.set("user_key", userKey)
				.set("room_id", roomId);

		return reactiveMongoTemplate.upsert(query, update, MucUnreadCount.class).then();
	}

	public Mono<Void> incrementForRoomMembers(Long roomId, List<String> memberKeys, String senderKey) {
		return Flux.fromIterable(memberKeys)
				//.filter(memberKey -> !memberKey.equals(senderKey)) // Don't notify the sender
				.flatMap(memberKey -> incrementUnreadCount(memberKey, roomId))
				.then();
	}

	/**
	 * Resets the unread count to zero when the user views the room.
	 */
	public Mono<Void> resetUnreadCount(String userKey, Long roomId) {
		String id = String.format("%s_%d", userKey, roomId);

		Query query = new Query(Criteria.where("_id").is(id));
		Update update = new Update().set("unread_count", 0);

		// Publish message to other devices to sync the unread message counts
		/*
        <message from='algomeet.com' to='user@algomeet.com' type='headline'>
        <sync xmlns='urn:xmpp:algomeet:sync:unread'>
          <muc room_id='101' unread_count='0' />
        </sync>
      </message> */
		if(userSessionRegistry.getSessions(userKey).size() > 1) {
			// Send it user has more that one session for synchronization
			MucCountUtil.composeMucCountSync(domainProperties.getDomain(), jidUtil.getBareJid(userKey), roomId, 0);
		}

		return reactiveMongoTemplate.updateFirst(query, update, MucUnreadCount.class).then();
	}

	/**
	 * Returns a list of all MUC rooms with unread messages for a specific user.
	 */
	public Flux<MucUnreadCount> getUnreadCountsByUser(String userKey) {
		Query query = new Query(Criteria.where("user_key").is(userKey)
				.and("unread_count").gt(0));
		return reactiveMongoTemplate.find(query, MucUnreadCount.class);
	}

	/**
	 * Aggregates the total unread count across all MUCs for a user.
	 */
	public Mono<Integer> getTotalUnreadCount(String userKey) {
		Query query = new Query(Criteria.where("user_key").is(userKey));
		return reactiveMongoTemplate.find(query, MucUnreadCount.class)
				.map(MucUnreadCount::getUnreadCount)
				.reduce(0, Integer::sum);
	}

	public Mono<Integer> getUnreadCount(String userKey, Long roomId) {
		String id = String.format("%s_%d", userKey, roomId);
		return reactiveMongoTemplate.findById(id, MucUnreadCount.class)
				.map(MucUnreadCount::getUnreadCount)
				.defaultIfEmpty(0);
	}     
}