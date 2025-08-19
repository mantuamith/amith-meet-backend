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
        if (message.getStatus() == null) {
            message.setStatus(com.algomeet.chatservice.model.MessageStatus.SENT);
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(java.time.Instant.now());
        }
        // reject self-messages
        // if (message.getSender().equals(message.getReceiver())) {
        //     throw new IllegalArgumentException("Sender and receiver cannot be the same");
        // }
        return messageRepository.save(message);
    }

    public int getUnreadCountFor(String receiver, String sender) {
        return messageRepository.countBySenderAndReceiverAndStatusNot(
                sender, receiver, com.algomeet.chatservice.model.MessageStatus.READ
        );
    }

    public List<RecentReceivedMessageResponse> getRecentMessages(String userId) {
        List<MessageDocument> all = messageRepository.findBySenderOrReceiver(userId, userId);
        if (all.isEmpty()) return List.of();

        // 2) group by "the other participant"
        Map<String, List<MessageDocument>> byContact = all.stream()
                .collect(Collectors.groupingBy(m ->
                        userId.equals(m.getSender()) ? m.getReceiver() : m.getSender()
                ));

        // 3) for each contact, pick latest message in the thread (either direction)
        List<RecentReceivedMessageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<MessageDocument>> e : byContact.entrySet()) {
            String contactId = e.getKey();
            List<MessageDocument> thread = e.getValue();

            // latest by timestamp
            MessageDocument latest = thread.stream()
                    .max(Comparator.comparing(MessageDocument::getTimestamp))
                    .orElse(null);

            long ts = latest != null ? latest.getTimestamp().toEpochMilli() : 0L;
            String lastText = latest != null ? latest.getContent() : null;

            // 4) unread count ONLY from contact -> user and not READ
            int unread = (int) thread.stream()
                    .filter(m -> contactId.equals(m.getSender()))   // from contact
                    .filter(m -> userId.equals(m.getReceiver()))    // to current user
                    .filter(m -> m.getStatus() != MessageStatus.READ)
                    .count();

            result.add(new RecentReceivedMessageResponse(contactId, lastText, ts, unread));
        }

        // 5) sort by latest desc
        return result.stream()
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
