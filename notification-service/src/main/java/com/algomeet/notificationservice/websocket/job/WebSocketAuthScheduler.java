package com.algomeet.notificationservice.websocket.job;

import java.util.Iterator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.util.MessageUtil;
import com.algomeet.notificationservice.websocket.NotificationWebSocketHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WebSocketAuthScheduler {

	@Value("${ws.unauthenticated.client.max-time-limit-in-seconds:1}")
	private int unauthenticatedUserMaxTimeLimit;
	
	 // Runs every 500 ms (0.5 seconds)
    @Scheduled(fixedRate = 500)
    public void runAuth() {   
    	log.debug("Validate unauthenticated web scoket connections");
    	validateUnauthenticatedConnections();        
    }
    
    private void validateUnauthenticatedConnections() {
    	Iterator<WebSocketSession> it = NotificationWebSocketHandler.getUnauthenticatedsessions().iterator();
    	while (it.hasNext()) {
    		WebSocketSession session = it.next();

    		long connectedTimeElapsedInSeconds = (System.currentTimeMillis() - (long) session.getAttributes().get(Constants.SESSION_ATTR_TIME_CONNECTED)) / 1000;
    		if (connectedTimeElapsedInSeconds > unauthenticatedUserMaxTimeLimit) {    			
    			// Force kill session
    			try {
    				session.sendMessage(new TextMessage(
    						MessageUtil.getMessage("unauthorizedAccess")));
    				session.close();
    			} catch(Exception ex) {};   	
    			
    			// Remove from list
    			it.remove();
    		}
    	}
    }
}