package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.DeliveryReceipt;
import com.algomeet.chatservice.dto.ReadReceipt;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.dto.UnreadCountResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageMapper messageMapper;

    // -------- RECENT / UNREAD --------
    public List<MessageResponse> getRecentUnreadMessages(String userId) {
        log.debug("[Unread] Fetching unread for user={}", userId);
        List<MessageResponse> out = messageRepository.findByReceiverAndStatusNot(userId, MessageStatus.READ)
                .stream()
                .sorted(Comparator.comparing(MessageDocument::getTimestamp).reversed())
                .peek(m -> log.trace("[Unread] docId={} from={} ts={}", m.getId(), m.getSender(), m.getTimestamp()))
                .map(messageMapper::toResponse)
                .toList();
        log.debug("[Unread] user={} items={}", userId, out.size());
        return out;
    }

    public void resetUnreadCount(String sender, String receiver) {
        log.info("[UnreadReset] sender={} -> receiver={}", sender, receiver);
        List<MessageDocument> unreadMessages =
                messageRepository.findBySenderAndReceiverAndStatusNot(sender, receiver, MessageStatus.READ);

        if (unreadMessages.isEmpty()) {
            log.debug("[UnreadReset] No unread to reset sender={} receiver={}", sender, receiver);
            sendUnreadCountUpdate(receiver);
            return;
        }

        unreadMessages.forEach(m -> m.setStatus(MessageStatus.READ));
        messageRepository.saveAll(unreadMessages);
        log.info("[UnreadReset] Marked READ count={} sender={} receiver={}", unreadMessages.size(), sender, receiver);

        sendUnreadCountUpdate(receiver);
    }

    public void sendUnreadCountUpdate(String userId) {
        var unread = messageRepository.findByReceiverAndStatusNot(userId, MessageStatus.READ);
        Map<String, Long> countsBySender = unread.stream()
                .collect(Collectors.groupingBy(MessageDocument::getSender, Collectors.counting()));

        List<UnreadCountResponse> summary = countsBySender.entrySet().stream()
                .map(e -> new UnreadCountResponse(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparing(UnreadCountResponse::getContactId))
                .toList();

        log.debug("[UnreadPush] user={} distinctContacts={} totalUnread={}",
                userId, summary.size(), unread.size());
        messagingTemplate.convertAndSendToUser(userId, "/queue/unread/count", summary);
    }

    // -------- PERSIST --------
    public MessageDocument saveMessage(MessageDocument message, Principal principal) {
        message.setSender(principal.getName());

        if (principal instanceof com.algomeet.chatservice.config.StompUserPrincipal up) {
            if (message.getSenderKey() == null) {
                message.setSenderKey(up.userKey());
            }
        }
        if (message.getStatus() == null) {
            message.setStatus(MessageStatus.SENT);
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now());
        }
        log.info("[Save] id?(pre) sender={} receiver={} status={} ts={}",
                message.getSender(), message.getReceiver(), message.getStatus(), message.getTimestamp());

        MessageDocument saved = messageRepository.save(message);
        log.info("[Save] id={} sender={} receiver={} status={} ts={}",
                saved.getId(), saved.getSender(), saved.getReceiver(), saved.getStatus(), saved.getTimestamp());
        return saved;
    }

    public int getUnreadCountFor(String receiver, String sender) {
        int c = messageRepository.countBySenderAndReceiverAndStatusNot(sender, receiver, MessageStatus.READ);
        log.debug("[UnreadCountFor] receiver={} from={} count={}", receiver, sender, c);
        return c;
    }

    public List<RecentReceivedMessageResponse> getRecentMessages(String userId) {
        log.debug("[Recent] Computing thread list for user={}", userId);
        List<MessageDocument> all = messageRepository.findBySenderOrReceiver(userId, userId);
        if (all.isEmpty()) {
            log.debug("[Recent] user={} no messages", userId);
            return List.of();
        }

        Map<String, List<MessageDocument>> byContact = all.stream()
                .collect(Collectors.groupingBy(m -> userId.equals(m.getSender()) ? m.getReceiver() : m.getSender()));

        List<RecentReceivedMessageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<MessageDocument>> e : byContact.entrySet()) {
            String contactId = e.getKey();
            List<MessageDocument> thread = e.getValue();

            MessageDocument latest = thread.stream()
                    .max(Comparator.comparing(MessageDocument::getTimestamp))
                    .orElse(null);

            long ts = latest != null ? latest.getTimestamp().toEpochMilli() : 0L;
            String lastText = latest != null ? latest.getContent() : null;

            int unread = (int) thread.stream()
                    .filter(m -> contactId.equals(m.getSender()))
                    .filter(m -> userId.equals(m.getReceiver()))
                    .filter(m -> m.getStatus() != MessageStatus.READ)
                    .count();

            result.add(new RecentReceivedMessageResponse(contactId, lastText, ts, unread));
            log.trace("[Recent] user={} contact={} latestId={} unread={}",
                    userId, contactId, latest != null ? latest.getId() : null, unread);
        }

        List<RecentReceivedMessageResponse> out = result.stream()
                .sorted(Comparator.comparingLong(RecentReceivedMessageResponse::getTimestamp).reversed())
                .toList();

        log.debug("[Recent] user={} threads={}", userId, out.size());
        return out;
    }

    // -------- DELIVERY RECEIPTS --------
    public void markMessagesAsDelivered(List<String> messageIds, String receiverUsername) {
        log.info("[Delivered] receiver={} ids={}", receiverUsername, messageIds);
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> receiverUsername.equals(m.getReceiver()))
                .filter(m -> m.getStatus() == MessageStatus.SENT)
                .toList();

        if (messages.isEmpty()) {
            log.debug("[Delivered] No eligible messages to mark. receiver={} ids={}", receiverUsername, messageIds);
            return;
        }

        messages.forEach(m -> m.setStatus(MessageStatus.DELIVERED));
        messageRepository.saveAll(messages);
        long nowSec = Instant.now().getEpochSecond();
        log.info("[Delivered] Updated count={} receiver={}", messages.size(), receiverUsername);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            DeliveryReceipt receipt = new DeliveryReceipt(receiverUsername, ids, nowSec);
            log.debug("[Delivered->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingTemplate.convertAndSendToUser(senderId, "/queue/delivery-receipts", receipt);
        });
    }

    // -------- READ RECEIPTS --------
    public void markMessagesAsRead(List<String> messageIds, String readerId) {
        log.info("[Read] reader={} ids={}", readerId, messageIds);
        List<MessageDocument> messages = messageRepository.findAllById(messageIds).stream()
                .filter(m -> readerId.equals(m.getReceiver()) && m.getStatus() != MessageStatus.READ)
                .toList();

        if (messages.isEmpty()) {
            log.debug("[Read] No eligible messages to mark. reader={} ids={}", readerId, messageIds);
            return;
        }

        messages.forEach(m -> m.setStatus(MessageStatus.READ));
        messageRepository.saveAll(messages);
        long nowSec = Instant.now().getEpochSecond();
        log.info("[Read] Updated count={} reader={}", messages.size(), readerId);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            ReadReceipt receipt = new ReadReceipt(readerId, ids, nowSec);
            log.debug("[Read->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", receipt);
        });

        // keep the reader's unread badges current
        sendUnreadCountUpdate(readerId);
    }
}
