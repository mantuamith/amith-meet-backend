package com.algomeet.xmpp.chatservice.service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.cluster.publisher.ReactiveClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.publisher.ReactiveMessageBackupRetentionUpdateEventPublisher;
import com.algomeet.xmpp.chatservice.redis.lock.ReactiveChatMessageRetentionLockManager;
import com.algomeet.xmpp.chatservice.stanza.SyncMessageRetentionStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingsFacade {
	private final ConversationSettingsCacheService conversationSettingsCacheService;
	private final ConversationSettingsService conversationSettingsService;
	private final ReactiveChatMessageRetentionLockManager reactiveChatMessageRetentionLockManager;
	private final ChatMessageService chatMessageService;
	private final JidUtil jidUtil;    
	private final ReactiveClusterMessagePublisher reactiveClusterMessagePublisher;
	private final ReactiveMessageBackupRetentionUpdateEventPublisher reactiveMessageBackupRetentionUpdateEventPublisher;
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

	private static final String LOCAL_LOCK_KEY = "xmpp:lock:update:conv-settings-retention:";
	private static final String RELEASE_LUA_SCRIPT = 
			"if redis.call('get', KEYS[1]) == ARGV[1] then " +
					"    return redis.call('del', KEYS[1]) " +
					"else " +
					"    return 0 " +
					"end";

	public Mono<Void> updateMessageRetention(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
		String lockValue = UUID.randomUUID().toString();
		long ttlMinutes = 5; 

		log.info("Attempting to acquire reactive lock for purge group conversation recovery...");
		String lockKey = LOCAL_LOCK_KEY + DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

		return reactiveRedisTemplate.opsForValue()
				.setIfAbsent(lockKey, lockValue, Duration.ofMinutes(ttlMinutes))
				.flatMap(acquired -> {
					if (Boolean.FALSE.equals(acquired)) {
						log.debug("Claim Abandoned Messages skipped: Another cluster node holds the lock key.");
						return Mono.error(new IllegalStateException("Could not acquire retention update lock. Process already running."));
					}

					log.debug("Distributed lock acquired [Token: {}]. Starting recovery loop...", lockValue);

					// Fixed syntax: Removed type declarations from the method call
					return reactiveChatMessageRetentionLockManager.isLockedReactive(userKey, peerKey)
							.flatMap(isLocked -> {
								if (isLocked) {
									return Mono.error(new IllegalStateException("Could not acquire retention update lock. Process already running."));
								}
								return executeUpdateMessageRetention(userKey, peerKey, messageRetentionDays);

							})
							// Ensures lock is released whether the upstream completes successfully or errors out
							.doFinally(signalType -> releaseLock(lockKey, lockValue).subscribe()); 
				})				
				.onErrorResume(ex -> {
					log.error("Failure encountered during reactive cleanup pipeline execution", ex);
					// Lock release is handled cleanly inside doFinally above, 
					// but we still handle/propagate the error appropriately here.
					return Mono.error(ex); 
				})
				.then();
	}

	private Mono<Void> releaseLock(String lockKey, String lockValue) {
		return reactiveRedisTemplate.execute(
				new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
				Collections.singletonList(lockKey),
				Collections.singletonList(lockValue)
				)
				.next()
				.doOnNext(released -> {
					if (Long.valueOf(1L).equals(released)) {
						log.debug("Distributed lock safely released [Token: {}].", lockValue);
					} else {
						log.warn("Lock release bypassed: Lock lease expired or was overridden.");
					}
				})
				.then();
	}

	public Mono<Void> executeUpdateMessageRetention(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
	    return Mono.defer(() -> {
	        Integer retentionDays = (messageRetentionDays != -1) ? messageRetentionDays : null;                     
	        String messageId = UuidCreator.getTimeOrderedEpoch().toString();

	        // 1. Compose the sync XMPP stanza payload
	        SyncMessageRetentionStanza syncStanza = SyncMessageRetentionStanza.builder() 
	                .id(messageId)
	                .from(jidUtil.getBareJid(userKey.toString()))
	                .to(jidUtil.getBareJid(peerKey.toString()))   
	                .retentiondays(messageRetentionDays)
	                .type(XmppMessageType.HEADLINE.getXmlValue())
	                .build();

	        // 2. Execute the sequential pipeline
	        return conversationSettingsService.saveOrUpdateRetentionDays(userKey, peerKey, messageRetentionDays)
	                .flatMap(savedSetting -> conversationSettingsCacheService.evictSettings(userKey, peerKey))
	                .then(chatMessageService.updatePurgeAtByToAndFrom(userKey, peerKey, retentionDays))
	                // 3. Publish to Redis Stream (reactive subscription)
	                .then(reactiveMessageBackupRetentionUpdateEventPublisher.publish(userKey, peerKey, messageRetentionDays))
	                // 4. Broadcast cluster sync message to other nodes (reactive subscription)
	                .then(reactiveClusterMessagePublisher.convertAndSendToUserReactive(
	                        messageId,
	                        peerKey.toString(),
	                        userKey.toString(),
	                        ChatType.CHAT,
	                        false,        // isAllowEcho
	                        true,         // shouldCarbon
	                        false,        // isAckStanza
	                        syncStanza.toXml(),
	                        null          // XmppPrincipal principal
	                ));
	    });
	}

}