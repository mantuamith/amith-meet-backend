package com.algomeet.signalservice.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.common.service.GroupClientService;

@Service
public class GroupCacheService extends AbstractGroupCache {

	public GroupCacheService(
			GroupClientService groupService,
			RedisTemplate<String, Object> redisTemplate) {
		
		// Invoking the typed superclass constructor explicitly
		super(groupService, redisTemplate);
	}
}
