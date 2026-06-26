package com.algomeet.mediaservice.service.impl;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.GroupClientService;
import com.algomeet.mediaservice.client.GroupClient;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor
public class GroupClientServiceImpl implements GroupClientService {
	
	private final GroupClient groupClient;

	@Override
	public Group getGroupById(String groupId) {
		return groupClient.getGroupById(groupId);
	}
}