package com.algomeet.notificationservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.dto.UserAuthInfo;
import com.algomeet.notificationservice.util.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {
	@Autowired
	private JwtUtil jwtUtil;

	public UserAuthInfo getAuthInfo(String token) {
		try {
			if (token.trim().startsWith(Constants.TOKEN_PREFIX)) {
				token = token.substring(Constants.TOKEN_PREFIX.length() + 1).trim();
			}
			
			Integer tenantId = jwtUtil.getTenantId(token) != null ? jwtUtil.getTenantId(token) : -1;
			
			return new UserAuthInfo(jwtUtil.getUserKey(token), tenantId);			 
		} catch(Exception ex) {
			log.error("Error authenticating user token {} ", ex.getMessage(), ex);
			return null;
		}
	}
}
