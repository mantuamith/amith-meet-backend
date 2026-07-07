package com.algomeet.xmpp.chatservice.redis.lock;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.common.util.DeterministicConversationIdUtil;

import reactor.core.publisher.Mono;

@Service
public class ReactiveChatMessageRetentionLockManager extends ChatMessageRetentionLockManager {
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;


	public Mono<Boolean> isLockedReactive(UUID userKey, UUID peerKey) {
		String lockKey = LOCK_KEY_PREFIX + DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

		return reactiveRedisTemplate.hasKey(lockKey);
	}

	public ReactiveChatMessageRetentionLockManager(
			@Qualifier("reactiveStringRedisTemplate")
			ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
		this.reactiveRedisTemplate = reactiveRedisTemplate;
	}
}
