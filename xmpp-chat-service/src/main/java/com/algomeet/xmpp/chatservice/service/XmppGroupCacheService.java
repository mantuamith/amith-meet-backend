package com.algomeet.xmpp.chatservice.service;

import java.util.List;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.common.service.GroupClientService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class XmppGroupCacheService extends AbstractGroupCache {
	private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

	public XmppGroupCacheService(
			GroupClientService groupService,
			RedisTemplate<String, Object> redisTemplate,
			ReactiveRedisTemplate<String, Object> reactiveRedisTemplate) {

		// Invoking the typed superclass constructor explicitly
		super(groupService, redisTemplate);
		this.reactiveRedisTemplate = reactiveRedisTemplate;
	}

	public Mono<Void> addToCache(List<Group> rooms) {  
		if (CollectionUtils.isEmpty(rooms)) {
			return Mono.empty();
		}

		return Flux.fromIterable(rooms)
				.flatMap(room -> {
					String key = getCacheKey(room.getId().toString());

					return reactiveRedisTemplate.hasKey(key)
							.flatMap(exists -> {
								Mono<Boolean> action = Boolean.TRUE.equals(exists) 
										? reactiveRedisTemplate.expire(key, cacheTtl)
										: reactiveRedisTemplate.opsForValue().set(key, room, cacheTtl);
								return action;
							});
				})
				.then(); 
	} 
}