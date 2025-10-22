package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.config.StompUserPrincipal;
import com.algomeet.chatservice.document.GroupDto;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.*;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.AppStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageDeleteService;
import com.algomeet.chatservice.service.MessageService;
import com.algomeet.chatservice.service.UserSessionService;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.dto.Notification.NotificationBuilder;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageRepository messageRepository;
    private final GroupClient groupClient;
    private final MessageMapper messageMapper;
    private final MessageService messageService;
    private final MessageDeleteService deleteService;
    private final NotificationService notificationService;
    private final UserSessionService userSessionService;
    private final SimpMessagingSyncTemplate messagingSyncTemplate;

    @MessageMapping("/chat")
    public void handleChatMessage(MessageDocument message, Principal principal) {
        log.info("[STOMP /chat] From: {}, To: {}, Content: {}", principal.getName(), message.getReceiver(), message.getContent());
        StompUserPrincipal up = (StompUserPrincipal) principal;
        String userKey = up.userKey();   // <-- UUID string (may be null on old tokens)
        String username = up.username();
        message.setSender(username);
        message.setTimestamp(message.getTimestamp());
        if (message.getStatus() == null) {
            message.setStatus(MessageStatus.SENT);
        }
        try {

            MessageDocument savedMessage = messageRepository.save(message);
            MessageResponse response = messageMapper.toResponse(savedMessage);
            List<String> failedMembers = new ArrayList<>();
            if (message.isGroupMessage()) {
                GroupDto group = groupClient.getGroupById(Long.parseLong(message.getGroupId()));
                for (String member : group.members) {
                    try {
                        if (!member.equals(message.getSender())) {
                        	messagingSyncTemplate.convertAndSendToUser(member, "/queue/messages", response);
                            messageService.sendUnreadCountUpdate(member); // real-time update

                            // Send push notification
                            // Todo: get the member user key
                            sendPushNotification(member, message.getContent(), NotificationType.GROUP_MESSAGE, member);
                        }
                    } catch (Exception e) {
                        // Fallback if one group member fails
                        // log.error("Failed to deliver to group member {}: {}", member, e.getMessage());
                        failedMembers.add(member);
                    }
                    if (!failedMembers.isEmpty()) {
                        savedMessage.setStatus(MessageStatus.FAILED);
                        savedMessage.setFailedRecipients(failedMembers);
                        messageRepository.save(savedMessage);

                        messagingSyncTemplate.convertAndSendToUser(
                                message.getSender(),
                                "/queue/errors",
                                "Message delivery failed for: " + String.join(", ", failedMembers)
                        );
                    }
                }
            } else {
                //Send the new message to the receiver
            	messagingSyncTemplate.convertAndSendToUser(message.getReceiver(), "/queue/messages", response);
                // Update receiver’s unread counts
                messageService.sendUnreadCountUpdate(message.getReceiver()); // real-time update
                int unread = messageService.getUnreadCountFor(message.getReceiver(), message.getSender());
                messagingSyncTemplate.convertAndSendToUser(
                        message.getReceiver(),
                        "/queue/unread/contact",
                        new UnreadCountResponse(message.getSender(), unread)

                );
                messagingSyncTemplate.convertAndSendToUser(message.getSender(), "/queue/update_message", response);

                // Also update sender’s side (so their recent panel shows latest msg)
                messageService.sendUnreadCountUpdate(message.getSender());
                int unreadForSender = messageService.getUnreadCountFor(message.getSender(), message.getReceiver());
                // Typically 0, but we still send event so FE refreshes “last message” row
                messagingSyncTemplate.convertAndSendToUser(
                        message.getSender(),
                        "/queue/unread/contact",
                        new UnreadCountResponse(message.getReceiver(), unreadForSender)
                );

                // Send push notifation
                sendPushNotification(message.getReceiverKey(), message.getContent(), NotificationType.DIRECT_MESSAGE, message.getReceiver());
            }
        } catch (Exception ex) {
            // Mark as FAILED and log
            message.setStatus(MessageStatus.FAILED);
            messageRepository.save(message);  // Update message with FAILED status

            // notify the sender
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "Message delivery failed. Try again."
            );
            /**
             * TODO: Enhancement
             *  retries for FAILED messages using a cron or event-based retry queue (Redis, Kafka, etc).
             */
        }
    }

    @GetMapping("/messages/history")
    public List<MessageResponse> getChatHistory(@RequestParam String user1, @RequestParam String user2) {
        return messageRepository
                .findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(user1, user2, user2, user1)
                .stream()
                .map(messageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @MessageMapping("/delivered")
    public void markAsDelivered(@Payload MessageStatusUpdate payload, Principal principal) {
        System.out.println("[STOMP /delivered] User: " + principal.getName() + ", Message IDs: " + payload.getMessageIds());
        messageService.markMessagesAsDelivered(payload, principal.getName());
    }

    @MessageMapping("/read")
    public void markAsRead(@Payload MessageStatusUpdate payload, Principal principal) {
        System.out.println("[STOMP /read] User: " + principal.getName() + ", Message IDs: " + payload.getMessageIds());
        messageService.markMessagesAsRead(payload, principal.getName());
    }

    @MessageMapping("/update-call-meta")
    public void updateCallMetadata(@Payload CallMessageMetaUpdate payload, Principal principal) {
        System.out.println("[STOMP /read] User: " + principal.getName() + ", Message IDs: " + payload.getMessageId());
        messageService.updateMessageCallMeta(payload, principal.getName());
    }

    @MessageMapping("/message-delete")
    public void wsDeleteMessage(@Payload MessageDeleteCommand cmd, Principal principal) {
        String requester = principal.getName();
        deleteService.deleteMessages(cmd.getMessageIds(), requester, cmd.isDeleteForEveryone());
        // No return; events are pushed to the appropriate queues inside the service.
    }


    @MessageMapping("/call")
    public void handleWebRTCSignal(SignalMessage message, Principal principal) {
        log.info("[STOMP /call] {} -> {} | Type: {}", principal.getName(), message.getTo(), message.getType());
        try {

            // Send calling event notification
            NotificationBuilder notifBuilder = Notification.builder()
                    .receiverIds(Set.of(message.getToKey())) // To must be using user_key UUID
                    .title(principal.getName() + " is calling")
                    .body(principal.getName() + " is calling")
                    .type(NotificationType.VIDEO_CALL.name().equalsIgnoreCase(message.getType())
                            ? NotificationType.VIDEO_CALL : NotificationType.AUDIO_CALL)
                    .tenantId(TenantContext.getCurrentTenant());
            notificationService.sendPush(notifBuilder.build());

            messagingSyncTemplate.convertAndSendToUser(
                    message.getTo(),
                    "/queue/call",
                    new SignalResponse(
                            message.getType(),
                            principal.getName(),
                            message.getPayload()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send signal to {}: {}", message.getTo(), e.getMessage());

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    "WebRTC signaling failed to deliver to: " + message.getTo()
            );
        }
    }

    /**
     * Used to set the user/client status
     *
     * @param message
     * @param principal
     */
    @MessageMapping("/app-status")
    public void handleInActive(AppStatusMessage message, Principal principal) {
        Set<SessionMetadata> sessions = userSessionService.getSessions(principal.getName());
        if (sessions != null) {
            sessions.forEach(s -> {
                s.setActive(AppStatus.ACTIVE == message.getStatus() ? true : false);
            });

            // Update sessions
            userSessionService.updateSessions(principal.getName(), sessions);
        }
    }

    /**
     * Used to send push notification for new message
     *
     * @param toKey
     * @param message
     * @param notifcationType
     * @param receiverUser
     */
    private void sendPushNotification(String toKey, String message, NotificationType notifcationType, String receiverUser) {
        // TODO: Need to finalize if we have to use username
        Set<SessionMetadata> sessions = userSessionService.getSessions(receiverUser);
        if (CollectionUtils.isEmpty(sessions)
                || sessions.iterator().next().isActive() == false) {

            Notification notif = Notification.builder()
                    .receiverIds(Set.of(toKey))
                    .type(notifcationType)
                    .title("You have new message")
                    .body(message)
                    .tenantId(TenantContext.getCurrentTenant())
                    .build();

            notificationService.sendPush(notif);
        }
    }

    @MessageMapping("/deliver/pending")
    public void deliverPending(Principal principal) {
        messageService.deliverAllPendingTo(principal.getName());
    }
}
