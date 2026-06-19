package com.algomeet.mediaservice.service.impl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.service.GroupCacheService;
import com.algomeet.common.service.GroupClientService;

@Service
public class GroupCacheServiceImpl extends GroupCacheService {

	public GroupCacheServiceImpl(
			GroupClientService groupService,
			RedisTemplate<String, Object> redisTemplate) {
		
		// Invoking the typed superclass constructor explicitly
		super(groupService, redisTemplate);
	}
}