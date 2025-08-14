package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    private final MessageMapper messageMapper;

    public List<MessageResponse> getRecentUnreadMessages(String userId) {
        return messageRepository.findByReceiverAndStatusNot(userId, MessageStatus.READ)
                .stream()
                .sorted(Comparator.comparing(MessageDocument::getTimestamp).reversed())
                .map(messageMapper::toResponse)
                .toList();
    }

    public void resetUnreadCount(String sender, String receiver) {
        List<MessageDocument> unreadMessages = messageRepository.findBySenderAndReceiverAndStatusNot(sender, receiver, com.algomeet.chatservice.model.MessageStatus.READ);

        for (MessageDocument message : unreadMessages) {
            message.setStatus(com.algomeet.chatservice.model.MessageStatus.READ);
        }

        messageRepository.saveAll(unreadMessages);
        sendUnreadCountUpdate(receiver);
    }

    public void sendUnreadCountUpdate(String userId) {
        List<MessageResponse> unreadSummary = getRecentUnreadMessages(userId);
        messagingTemplate.convertAndSendToUser(userId, "/queue/unread/count", unreadSummary);
    }

    public MessageDocument saveMessage(MessageDocument message, Principal principal) {
        message.setSender(principal.getName());
        message.setTimestamp(Instant.now());
        message.setStatus(com.algomeet.chatservice.model.MessageStatus.SENT);
        return messageRepository.save(message);
    }

    public int getUnreadCountFor(String receiver, String sender) {
        return messageRepository.countBySenderAndReceiverAndStatusNot(
                sender, receiver, com.algomeet.chatservice.model.MessageStatus.READ
        );
    }

    public List<RecentReceivedMessageResponse> getRecentMessages(String userId) {
        List<MessageDocument> allMessages = messageRepository.findByReceiver(userId);

        return allMessages.stream()
                .collect(Collectors.groupingBy(MessageDocument::getSender))
                .entrySet().stream()
                .map(entry -> {
                    String contactId = entry.getKey();
                    List<MessageDocument> messages = entry.getValue();
                    MessageDocument latest = messages.stream()
                            .max(Comparator.comparing(MessageDocument::getTimestamp))
                            .orElse(null);

                    long timestamp = latest != null ? latest.getTimestamp().toEpochMilli() : 0;
                    String newMessage = latest != null ? latest.getContent() : null;
                    long unreadCount = messages.stream()
                            .filter(m -> m.getStatus() != MessageStatus.DELIVERED)
                            .count();

                    return new RecentReceivedMessageResponse(contactId, newMessage, timestamp, (int) unreadCount);
                })
                .sorted(Comparator.comparingLong(RecentReceivedMessageResponse::getTimestamp).reversed())
                .toList();
    }

    public void markMessagesAsDelivered(List<String> messageIds, String userId) {
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> m.getReceiver().equals(userId) && m.getStatus() == MessageStatus.SENT)
                .toList();

        for (MessageDocument m : messages) {
            m.setStatus(MessageStatus.DELIVERED);
        }

        messageRepository.saveAll(messages);
    }

    public void markMessagesAsRead(List<String> messageIds, String userId) {
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> m.getReceiver().equals(userId) && m.getStatus() != MessageStatus.READ)
                .toList();

        for (MessageDocument m : messages) {
            m.setStatus(MessageStatus.READ);
        }

        messageRepository.saveAll(messages);

        // Update unread count on UI
        sendUnreadCountUpdate(userId);
    }


}
