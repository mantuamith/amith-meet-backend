package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.DeliveryReceipt;
import com.algomeet.chatservice.dto.ReadReceipt;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
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
        List<MessageDocument> unreadMessages = messageRepository.findBySenderAndReceiverAndStatusNot(sender, receiver, MessageStatus.READ);

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
        List<MessageDocument> allVisible = messageRepository.findVisibleForViewer(
                userId,
                Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "_id"))
        );
        if (allVisible.isEmpty()) return List.of();

        // group by "the other participant"
        Map<String, List<MessageDocument>> byContact = allVisible.stream()
                .collect(Collectors.groupingBy(m -> {
                    String sender = String.valueOf(m.getSender());
                    String receiver = String.valueOf(m.getReceiver());
                    return userId.equals(sender) ? receiver : sender;
                }));

        // build response per contact
        List<RecentReceivedMessageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<MessageDocument>> e : byContact.entrySet()) {
            String contactId = e.getKey();
            List<MessageDocument> thread = e.getValue();

            MessageDocument latest = thread.stream()
                    .filter(m -> m.isVisibleTo(userId))
                    .max(Comparator.comparing(MessageDocument::getTimestamp))
                    .orElse(null);
            if (latest == null) {
                // No visible messages remain in this thread → skip it entirely
                continue;
            }

            long ts = latest.getTimestamp().getEpochSecond();
            String lastText =  latest.getContent();

            int unread = (int) thread.stream()
                    .filter(m -> contactId.equals(m.getSender()))
                    .filter(m -> userId.equals(m.getReceiver()))
                    .filter(m -> m.getStatus() != MessageStatus.READ)
                    .count();

            result.add(new RecentReceivedMessageResponse(contactId, lastText, ts, unread));
        }

        return result.stream()
                .sorted(Comparator.comparingLong(RecentReceivedMessageResponse::getTimestamp).reversed())
                .toList();
    }

    // MessageService.java
    public void markMessagesAsDelivered(List<String> messageIds, String receiverUsername) {
        // Messages that were sent to THIS receiver and still in SENT state
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> receiverUsername.equals(m.getReceiver()))
                .filter(m -> m.getStatus() == MessageStatus.SENT)
                .toList();

        if (messages.isEmpty()) return;

        // 1) update state
        messages.forEach(m -> m.setStatus(MessageStatus.DELIVERED));
        messageRepository.saveAll(messages);

        long nowSec = java.time.Instant.now().getEpochSecond();

        // 2) group by sender and notify each sender
        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(java.util.stream.Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();

            DeliveryReceipt receipt = new DeliveryReceipt(
                    receiverUsername,  // contactId: who received them
                    ids,               // which message IDs
                    nowSec             // when delivered (epoch seconds)
            );

            // -> /user/{senderId}/queue/delivery-receipts
            messagingTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/delivery-receipts",
                    receipt
            );
        });
    }


    public void markMessagesAsRead(List<String> messageIds, String readerId) {
        // 1) Load all messages and filter to those actually sent to reader
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> m.getReceiver().equals(readerId) && m.getStatus() != MessageStatus.READ)
                .toList();

        if (messages.isEmpty()) {
            return;
        }

        // 2) Mark as READ
        messages.forEach(m -> m.setStatus(MessageStatus.READ));
        messageRepository.saveAll(messages);

        long nowSec = Instant.now().getEpochSecond();

        // 3) Group by sender, so each sender gets a single receipt payload
        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();

            // construct a simple DTO for the read receipt
            ReadReceipt receipt = new ReadReceipt(
                    readerId,   // contactId = who read them
                    ids,        // which messages
                    nowSec      // when
            );

            // 4) Send STOMP frame to the original sender
            messagingTemplate.convertAndSendToUser(
                    senderId,                      // the user who sent the messages
                    "/queue/read-receipts",        // sender subscribes here
                    receipt
            );
        });

        // 5) Still update unread counters for the reader’s UI
        sendUnreadCountUpdate(readerId);
    }



}
