package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.config.StompUserPrincipal;
import com.algomeet.chatservice.document.GroupDto;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.MessageDeleteCommand;
import com.algomeet.chatservice.dto.MessageStatusUpdate;
import com.algomeet.chatservice.dto.SignalMessage;
import com.algomeet.chatservice.dto.SignalResponse;
import com.algomeet.chatservice.dto.UnreadCountResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageDeleteService;
import com.algomeet.chatservice.service.MessageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @MessageMapping("/chat")
    public void handleChatMessage(MessageDocument message, Principal principal) {
        log.info("[STOMP /chat] From: {}, To: {}, Content: {}", principal.getName(), message.getReceiver(), message.getContent());
        StompUserPrincipal up = (StompUserPrincipal) principal;
        String userKey = up.userKey();   // <-- UUID string (may be null on old tokens)
        String username = up.username();
        message.setSender(username);
        message.setTimestamp(Instant.now());
        if (message.getStatus() == null)
        {
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
                            messagingTemplate.convertAndSendToUser(member, "/queue/messages", response);
                            messageService.sendUnreadCountUpdate(member); // real-time update
                        }
                    }catch (Exception e) {
                        // Fallback if one group member fails
                       // log.error("Failed to deliver to group member {}: {}", member, e.getMessage());
                        failedMembers.add(member);
                    }
                    if (!failedMembers.isEmpty()) {
                        savedMessage.setStatus(MessageStatus.FAILED);
                        savedMessage.setFailedRecipients(failedMembers);
                        messageRepository.save(savedMessage);

                        messagingTemplate.convertAndSendToUser(
                                message.getSender(),
                                "/queue/errors",
                                "Message delivery failed for: " + String.join(", ", failedMembers)
                        );
                    }
                }
            } else {
                //Send the new message to the receiver
                messagingTemplate.convertAndSendToUser(message.getReceiver(), "/queue/messages", response);
                // Update receiver’s unread counts
                messageService.sendUnreadCountUpdate(message.getReceiver()); // real-time update
                int unread = messageService.getUnreadCountFor(message.getReceiver(), message.getSender());
                messagingTemplate.convertAndSendToUser(
                        message.getReceiver(),
                        "/queue/unread/contact",
                        new UnreadCountResponse(message.getSender(), unread)

                );
                messagingTemplate.convertAndSendToUser(message.getSender(), "/queue/update_message", response);

                // Also update sender’s side (so their recent panel shows latest msg)
                messageService.sendUnreadCountUpdate(message.getSender());
                int unreadForSender = messageService.getUnreadCountFor(message.getSender(), message.getReceiver());
                // Typically 0, but we still send event so FE refreshes “last message” row
                messagingTemplate.convertAndSendToUser(
                        message.getSender(),
                        "/queue/unread/contact",
                        new UnreadCountResponse(message.getReceiver(), unreadForSender)
                );

            }
        }catch (Exception ex) {
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
        messageService.markMessagesAsDelivered(payload.getMessageIds(), principal.getName());
    }

    @MessageMapping("/read")
    public void markAsRead(@Payload MessageStatusUpdate payload, Principal principal) {
        System.out.println("[STOMP /read] User: " + principal.getName() + ", Message IDs: " + payload.getMessageIds());
        messageService.markMessagesAsRead(payload.getMessageIds(), principal.getName());
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
            messagingTemplate.convertAndSendToUser(
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
}
