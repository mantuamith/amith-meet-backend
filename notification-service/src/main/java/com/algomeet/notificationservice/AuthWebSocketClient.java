package com.algomeet.notificationservice;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

public class AuthWebSocketClient extends Endpoint {
	@Override
    public void onOpen(Session session, EndpointConfig config) {
        System.out.println("Connected: " + session.getId());
        session.addMessageHandler(String.class, message -> {
            System.out.println("Message: " + message);
        });
        try {
            session.getBasicRemote().sendText("{\"type\":\"ping\"}");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Closed: " + closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        thr.printStackTrace();
    }
    
    public static void main(String[] args) throws Exception {
        String token = "Bearer your_jwt_token_here";

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();

         
        ClientEndpointConfig clientConfig = ClientEndpointConfig.Builder.create()
        	    .configurator(new ClientEndpointConfig.Configurator() {
        	        @Override
        	        public void beforeRequest(Map<String, List<String>> headers) {
        	            headers.put("Authorization", Collections.singletonList("Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYWRkb3guYWxnb2ZyYW1lQGdtYWlsLmNvbSIsInVzZXJuYW1lIjoibWFkZG94IiwiaWQiOjEsInR5cGUiOiJyZWZyZXNoIiwic2lkIjoiYjY5NWFjZTktYzNkMy00YWJlLTgyN2QtNWRiZGQwYWIxZGM4IiwidXNlcl9rZXkiOiIyZmMzNWNhZS1lMGI3LTQwYTUtYjJhYS1lODYyMDY3MzBlOTkiLCJpYXQiOjE3NTgzNjIxMzksImV4cCI6NDM1MDM2MjEzOX0.s8A4kcPA_fsomNkEcWhsW4LDTT5OsZjSntIwPvNIGVU"));
        	        }
        	    })
        	    .build();

        Session session = container.connectToServer(
            AuthWebSocketClient.class,
            clientConfig,
            new URI("ws://localhost:8089/notifications/subscribe")
        );

        // Keep the client alive
        Thread.sleep(60000);
        session.close();
    }
}