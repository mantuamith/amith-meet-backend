package com.algomeet.xmpp.chatservice.service;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
	private final UserSessionRegistry userSessionRegistry;
	
	public void removeSession(String userKey, String sessionId) {
		userSessionRegistry.removeSession(userKey, sessionId);
		
	    log.debug("Session {} successfully removed from Redis", sessionId);
	}
}
