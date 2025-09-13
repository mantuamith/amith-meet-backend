package com.algomeet.chatservice.registry;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    // user → set of sessionIds
    private final Map<String, Set<SessionMetadata>> userSessions = new ConcurrentHashMap<>();

    public void addSession(String username, String sessionId) {
    	userSessions.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet())
    	.add(
    		SessionMetadata.builder()
    			.isActive(true)
    			.sessionId(sessionId)
    			.build());
    }

    public void removeSession(String username, String sessionId) {
        Set<SessionMetadata> sessions = userSessions.get(username);
        if (sessions != null) {
        	Iterator<SessionMetadata> it = sessions.iterator();
        	while (it.hasNext()) {
        		if (sessionId.equals(it.next().getSessionId())) {
        			sessions.remove(sessionId);
        		}
        	}
        			
            if (sessions.isEmpty()) {
                userSessions.remove(username);
            }
        }
    }

    public Set<SessionMetadata> getSessions(String username) {
        return userSessions.getOrDefault(username, Set.of());
    }

    public Map<String, Set<SessionMetadata>> getAllSessions() {
        return userSessions;
    }
}
