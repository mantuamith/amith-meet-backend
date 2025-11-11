package com.algomeet.chatservice.controller;

import java.security.Principal;

import org.springframework.beans.BeanUtils;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.chatservice.dto.signalling.SignalChatSyncMessage;
import com.algomeet.chatservice.dto.signalling.SignalChatSyncResponse;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code ChatSyncController} handles chat history synchronization signaling
 * between multiple user devices participating in an end-to-end encrypted
 * conversation using the OLM (Olm / Matrix-style) encryption model.
 * <p>
 * This controller listens for STOMP messages sent to the destination
 * {@code /app/chat/sync} and relays synchronization payloads between
 * authenticated devices of the same user or between peers, enabling
 * secure message history alignment.
 * </p>
 *
 * <h2>Overview</h2>
 * <ul>
 *   <li>Receives synchronization signaling messages from a WebSocket client.</li>
 *   <li>Copies message data into a response DTO for sending.</li>
 *   <li>Uses {@link SimpMessagingSyncTemplate} to forward the payload to the target device’s user queue.</li>
 *   <li>In case of delivery errors, sends an error notification to the sender’s error queue.</li>
 * </ul>
 *
 * <h2>Use Case</h2>
 * This controller supports OLM message synchronization across devices,
 * such as transferring decrypted message indexes, device sessions, or chat
 * history fragments between a mobile app and a web client.
 *
 * <h2>Security</h2>
 * Each WebSocket session is authenticated, and the {@link Principal} object
 * identifies the sender. The destination user is extracted from the
 * {@link SignalChatSyncMessage#getTo()} field, ensuring messages are
 * directed only to authorized targets.
 *
 * @author 
 * @since 2025-11
 */
@RestController
@AllArgsConstructor
@Slf4j
public class ChatSyncController {	
	/** 
     * Default messaging template used for simple asynchronous message sending. 
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Specialized messaging template that supports synchronized message delivery
     * to multiple chat-service instances.
     */
    private final SimpMessagingSyncTemplate messagingSyncTemplate;

    /**
     * Handles STOMP messages for chat history synchronization between devices for messages
     * encrypted using OLM (1:1).
     * <p>
     * This endpoint is mapped to {@code /app/chat/sync} and acts as the signaling
     * layer for OLM-based synchronization, typically used to bootstrap or
     * reconcile message states between different user devices. Transferring of messages 
     * should be implemented using peer-to-peer webRTC and messages must be encrypted using Matrix-OLM
     * before they will be transferred to the target device.
     * </p>
     *
     * <h3>Flow:</h3>
     * <ol>
     *   <li>Logs sender, receiver, and message type.</li>
     *   <li>Copies properties from the incoming {@link SignalChatSyncMessage} into
     *       a {@link SignalChatSyncResponse} DTO.</li>
     *   <li>Uses {@link SimpMessagingSyncTemplate#convertAndSendToUser(String, String, Object)}
     *       to forward the response to the recipient’s {@code /queue/chat/sync} endpoint.</li>
     *   <li>Handles and logs any exceptions that occur during delivery and sends
     *       an error message to the sender’s {@code /queue/errors} endpoint.</li>
     * </ol>
     *
     * @param message   the synchronization signaling payload containing sender,
     *                  receiver, and synchronization metadata.
     * @param principal the authenticated WebSocket principal representing
     *                  the current sender device or session.
     */

	@MessageMapping("/chat/sync")
    public void handleChatSyncSignaling(SignalChatSyncMessage message, Principal principal) {
        log.info("[STOMP /chat/sync] {} -> {} | Type: {}", principal.getName(), message.getTo(), message.getType());
        try {	
        	SignalChatSyncResponse response = new SignalChatSyncResponse();
            BeanUtils.copyProperties(message, response);
            
            messagingSyncTemplate.convertAndSendToUser(
                    message.getTo(),
                    "/queue/chat/sync",
                    response
            );
        } catch (Exception ex) {
            log.error("Failed to send message to {}: {}", message.getTo(), ex.getMessage(), ex);

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "WebRTC signaling message failed to deliver to: " + message.getTo()
            );
        }
    }
}
