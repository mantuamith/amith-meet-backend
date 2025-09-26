package com.algomeet.authservice.service;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.PageResponse;
import com.algomeet.authservice.dto.SearchUsersFilter;
import com.algomeet.authservice.dto.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
	private final UserClient userClient;
	
	public PageResponse<UserResponse> searchUsers(SearchUsersFilter filter) {
		PageResponse<UserResponse> page = null;
		try {
				page =  userClient.findAll(filter.getUsername(),
				filter.getEmail(),
				filter.getPhoneNumber(),
				filter.getPage(),
				filter.getSize(),
				filter.getSortBy(),
				filter.getDirection(),
				filter.getTenantId()				
				).getBody();	
		} catch(Exception ex) {
			log.warn("user-service findAll failed: {}", ex.toString());
			return null;
		}
		return page;
	}
	
	public UserResponse findUserById(Long id) {
		UserResponse resp = userClient.findUserById(id);		
		return resp;
	}
	
	public UserResponse findUserByUserKey(UUID userKey) {
		UserResponse resp = userClient.findUserByUserKey(userKey);		
		return resp;
	}
}
