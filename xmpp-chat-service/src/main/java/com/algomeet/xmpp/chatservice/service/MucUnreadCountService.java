package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.MucUnreadCount;
import com.algomeet.xmpp.chatservice.enums.ChatType;
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
	private final ClusterMessagePublisher clusterMessagePublisher;

	/**
	 * Increments the unread count for a specific user in a specific room.
	 * Uses upsert to ensure the document exists.
	 */
	public Mono<Void> incrementUnreadCount(String userKey, String roomId) {
		String id = String.format("%s_%s", userKey, roomId);

		Query query = new Query(Criteria.where("_id").is(id));
		Update update = new Update()
				.inc("unread_count", 1)
				.set("user_key", userKey)
				.set("room_id", roomId)
				.set("last_increment_at", Instant.now().toEpochMilli());

		return reactiveMongoTemplate.upsert(query, update, MucUnreadCount.class).then();
	}

	public Mono<Void> incrementForRoomMembers(String roomId, List<String> memberKeys, String senderKey) {
		return Flux.fromIterable(memberKeys)
				.filter(memberKey -> !memberKey.equals(senderKey)) // Don't notify the sender
				.flatMap(memberKey -> incrementUnreadCount(memberKey, roomId))
				.then();
	}
	
	/**
	 * Non-blocking decrement of the unread count for a specific MUC room.
	 * Ensures the count does not drop below zero using an atomic operation.
	 */
	public Mono<MucUnreadCount> decrementUnreadCount(String userKey, String roomId, XmppPrincipal principal) {
		String id = String.format("%s_%d", userKey, roomId);

		// Atomic decrement: only execute if the current count is greater than 0
		Query query = new Query(Criteria.where("_id").is(id).and("unread_count").gt(0));
		Update update = new Update()
				.inc("unread_count", -1)
				.set("last_decrement_at", Instant.now().toEpochMilli());

		return reactiveMongoTemplate.updateFirst(query, update, MucUnreadCount.class)
				.then(reactiveMongoTemplate.findById(id, MucUnreadCount.class))
				.flatMap(mucUnreadCount -> {
					// --- XMPP Device Synchronization ---
					// If the user has multiple active sessions, broadcast the new count 
					// to ensure consistent UI badges across all devices.
					if (userSessionRegistry.getSessions(userKey).size() > 1) {
						String payload = MucCountUtil.composeMucCountSync(
								domainProperties.getDomain(),
								jidUtil.getBareJid(userKey),
								roomId,
								mucUnreadCount.getUnreadCount()
						);
						clusterMessagePublisher.convertAndSendToUser(id, userKey, userKey, ChatType.CHAT, false, payload, principal);
					}
					return Mono.just(mucUnreadCount);
				});
	}

	/**
	 * Resets the unread count to zero when the user views the room.
	 */
	public Mono<Void> resetUnreadCount(String userKey, String roomId) {
		String id = String.format("%s_%d", userKey, roomId);

		Query query = new Query(Criteria.where("_id").is(id));
		Update update = new Update()
				.set("unread_count", 0)
				.set("last_decrement_at", Instant.now().toEpochMilli());

		// Publish message to other devices to sync the unread message counts
		/*
        <message from='algomeet.com' to='user@algomeet.com' type='headline'>
        <sync xmlns='urn:xmpp:algomeet:sync:unread'>
          <muc room_id='101' unread_count='0' />
        </sync>
      </message> */
		if(userSessionRegistry.getSessions(userKey).size() > 1) {
			// Send it user has more that one session for synchronization
			String payload = MucCountUtil.composeMucCountSync(domainProperties.getDomain(), jidUtil.getBareJid(userKey), roomId, 0);
			clusterMessagePublisher.convertAndSendToUser(id, userKey, userKey, ChatType.CHAT, payload);
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

	public Mono<Integer> getUnreadCount(String userKey, String roomId) {
		String id = String.format("%s_%d", userKey, roomId);
		return reactiveMongoTemplate.findById(id, MucUnreadCount.class)
				.map(MucUnreadCount::getUnreadCount)
				.defaultIfEmpty(0);
	}     
}