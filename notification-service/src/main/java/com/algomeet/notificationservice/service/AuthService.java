package com.algomeet.notificationservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.util.JwtUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {
	@Autowired
	private JwtUtil jwtUtil;

	public String getUsername(String token) {
		try {
			if (token.trim().startsWith(Constants.TOKEN_PREFIX)) {
				token = token.substring(Constants.TOKEN_PREFIX.length() + 1).trim();
			}

			return jwtUtil.extractUsername(token);
		} catch(Exception ex) {
			log.error("Error authenticating user token {} ", ex.getMessage(), ex);
			return null;
		}
	}
}
