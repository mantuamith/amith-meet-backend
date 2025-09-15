package com.algomeet.notificationservice.websocket.auth;

import java.util.Iterator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.util.MessageUtil;
import com.algomeet.notificationservice.websocket.NotificationWebSocketHandler;

import jakarta.annotation.PostConstruct;

@Component
public class ResilientWebSocketAuthWorker {
	private Thread workerThread;

	@Value("${ws.unauthenticated.client.max-time-limit-in-seconds:1}")
	private int unauthenticatedUserMaxTimeLimit;
	
    @PostConstruct
    public void start() {
        startWorker();
    }

    private void startWorker() {
        workerThread = new Thread(() -> {
            while (true) {
                try {
                    doWork();
                } catch (Exception e) {                    
                    // sleep a bit before retry
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }
        }, "resilient-auth-worker");

        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void doWork() {
    	Iterator<WebSocketSession> it  = NotificationWebSocketHandler.getUnauthenticatedsessions().iterator();
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
    			NotificationWebSocketHandler.removeFromAuthenticatedSessions(session);
    		}
    	}

    	try {
    		Thread.sleep(500);
    	} catch (InterruptedException ignored) {}
    }
}