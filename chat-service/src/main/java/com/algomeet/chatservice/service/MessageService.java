package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.CallMetaData;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.*;
import com.algomeet.chatservice.dto.clearchat.ChatClearedEvent;
import com.algomeet.chatservice.dto.signalling.CallMessageMetaUpdate;
import com.algomeet.chatservice.dto.signalling.CallMetaUpdatedEvent;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.CallType;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.dto.Notification.NotificationBuilder;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import org.springframework.stereotype.Service;


import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;     // <— THIS ONE
import org.springframework.data.mongodb.core.query.Update;

import static com.algomeet.chatservice.util.MessageUtil.wrapWithBraces;
import static org.springframework.data.mongodb.core.query.Criteria.where;


@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final SimpMessagingSyncTemplate messagingSyncTemplate;
    private final MessageMapper messageMapper;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

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
        messagingSyncTemplate.convertAndSendToUser(userId, "/queue/unread/count", summary);
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

        List<MessageDocument> visible = all.stream()
                .filter(m -> m.isVisibleTo(userId))
                .toList();

        if (visible.isEmpty()) return List.of();

        Map<String, List<MessageDocument>> byContact = visible.stream()
                .collect(Collectors.groupingBy(m -> userId.equals(m.getSender()) ? m.getReceiver() : m.getSender()));

        List<RecentReceivedMessageResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<MessageDocument>> e : byContact.entrySet()) {
            String contactId = e.getKey();
            List<MessageDocument> thread = e.getValue();

            MessageDocument latest = thread.stream()
                    .max(Comparator.comparing(MessageDocument::getTimestamp))
                    .orElse(null);
            if (latest == null) {
                // entire thread is invisible → drop from recent
                continue;
            }

            long ts = latest.getTimestamp().toEpochMilli();
            String lastText = latest.getContent();

            int unread = (int) thread.stream()
                    .filter(m -> contactId.equals(m.getSender()))
                    .filter(m -> userId.equals(m.getReceiver()))
                    .filter(m -> m.getStatus() != MessageStatus.READ)
                    .count();

            result.add(new RecentReceivedMessageResponse(contactId, lastText, ts, unread));
            log.trace("[Recent] user={} contact={} latestId={} unread={}",
                    userId, contactId, latest.getId(), unread);
        }

        List<RecentReceivedMessageResponse> out = result.stream()
                .sorted(Comparator.comparingLong(RecentReceivedMessageResponse::getTimestamp).reversed())
                .toList();

        log.debug("[Recent] user={} threads={}", userId, out.size());
        return out;
    }

    // -------- DELIVERY RECEIPTS --------
    public void markMessagesAsDelivered(MessageStatusUpdate deliverStatus, String receiverUsername) {
        log.info("[Delivered] receiver={} ids={}", receiverUsername, deliverStatus.getMessageIds());
        List<MessageDocument> messages = messageRepository.findAllById(deliverStatus.getMessageIds()).stream()
                .filter(m -> receiverUsername.equals(m.getReceiver()))
                .filter(m -> m.getStatus() == MessageStatus.SENT)
                .toList();

        if (messages.isEmpty()) {
            log.debug("[Delivered] No eligible messages to mark. receiver={} ids={}", receiverUsername, deliverStatus.getMessageIds());
            return;
        }

        messages.forEach(m -> {m.setStatus(MessageStatus.DELIVERED);
            m.setMsgDeliveredTimeStamp(deliverStatus.getStatusTimeStamp());
        });
        messageRepository.saveAll(messages);
        long nowSec = deliverStatus.getStatusTimeStamp();
        log.info("[Delivered] Updated count={} receiver={}", messages.size(), receiverUsername);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            DeliveryReceipt receipt = new DeliveryReceipt(receiverUsername, ids, nowSec);
            log.debug("[Delivered->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingSyncTemplate.convertAndSendToUser(senderId, "/queue/delivery-receipts", receipt);
        });
    }

    // -------- READ RECEIPTS --------
    public void markMessagesAsRead(MessageStatusUpdate readUpdate, String readerId) {
        log.info("[Read] reader={} ids={}", readerId, readUpdate.getMessageIds());
        List<MessageDocument> messages = messageRepository.findAllById(readUpdate.getMessageIds()).stream()
                .filter(m -> readerId.equals(m.getReceiver()) && m.getStatus() != MessageStatus.READ)
                .toList();

        if (messages.isEmpty()) {
            log.debug("[Read] No eligible messages to mark. reader={} ids={}", readerId, readUpdate.getMessageIds());
            return;
        }

        messages.forEach(m -> {
            m.setStatus(MessageStatus.READ);
            m.setMsgReadTimeStamp(readUpdate.getStatusTimeStamp());
        });
        messageRepository.saveAll(messages);
        long nowSec = readUpdate.getStatusTimeStamp();
        log.info("[Read] Updated count={} reader={}", messages.size(), readerId);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            ReadReceipt receipt = new ReadReceipt(readerId, ids, nowSec);
            log.debug("[Read->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingSyncTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", receipt);
        });

        // keep the reader's unread badges current
        sendUnreadCountUpdate(readerId);
    }

    public void markMessagesAsRead(String reqSenderId, String contactId) {
        log.info("[Read] reader={} ", contactId);
        List<MessageDocument> messages = messageRepository.findByReceiver(contactId).stream()
                .filter(m -> contactId.equals(m.getReceiver()) && m.getStatus() != MessageStatus.READ)
                .toList();

        if (messages.isEmpty()) {
            log.debug("[Read] No eligible messages to mark. reader={}", contactId);
            return;
        }

        messages.forEach(m -> m.setStatus(MessageStatus.READ));
        messageRepository.saveAll(messages);
        long nowSec = Instant.now().getEpochSecond();
        log.info("[Read] Updated count={}", messages.size());

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getReceiver));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            ReadReceipt receipt = new ReadReceipt(contactId, ids, nowSec);
            log.debug("[Read->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingSyncTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", receipt);
        });
        // keep the reader's unread badges current
        sendUnreadCountUpdate(reqSenderId);
    }

    public void updateMessageCallMeta(CallMessageMetaUpdate payload, String updaterUsername) {
        if (payload == null || payload.getMessageId() == null) {
            log.warn("[CALL META] Missing payload or messageId");
            return;
        }

        Optional<MessageDocument> opt = messageRepository.findById(payload.getMessageId());
        if (opt.isEmpty()) {
            log.debug("[CALL META] No Message found for {}", payload.getMessageId());
            return;
        }

        MessageDocument msg = opt.get();

        // authorization: only sender or receiver may update this message’s call meta
        boolean isParticipant = updaterUsername.equals(msg.getSender()) || updaterUsername.equals(msg.getReceiver());
        if (!isParticipant) {
            log.warn("[CALL META] User {} is not a participant of message {}", updaterUsername, msg.getId());
            return;
        }

        // persist metadata
        CallMetaData newMeta = payload.getCallMetaData();
        msg.setCallMetaData(newMeta);
        messageRepository.save(msg);

        // figure out counterparty
        String other = updaterUsername.equals(msg.getSender()) ? msg.getReceiver() : msg.getSender();

        // push a compact event to the other user
        long now = java.time.Instant.now().getEpochSecond();
        CallMetaUpdatedEvent evt = new CallMetaUpdatedEvent(
                msg.getId(),
                updaterUsername,
                other,
                newMeta,
                now
        );

        // STOMP destination for call meta updates
        messagingSyncTemplate.convertAndSendToUser(other, "/queue/call-meta", evt);
        
        // Send missed call notification
        if (payload.getCallMetaData() != null 
        		&& payload.getCallMetaData() != null
        		&& Boolean.valueOf(payload.getCallMetaData().getIsMissedCall())) {

        	NotificationBuilder notifBuilder = Notification.builder()
        			.receiverIds(Set.of(msg.getReceiverKey())) // To must be using user_key UUID
        			.title(wrapWithBraces(msg.getSender()) + " missed call")
        			.body(wrapWithBraces(msg.getSender()) + " missed call")
        			.type(CallType.AUDIO_VIDEO_TYPE == payload.getCallMetaData().getCallType()
        			? NotificationType.VIDEO_MISSED_CALL : NotificationType.AUDIO_MISSED_CALL)
        			.tenantId(TenantContext.getCurrentTenant());        	

        	notificationService.sendPush(notifBuilder.build());
        }

        log.info("[CALL META] Updated for message {} by {} -> notified {}", msg.getId(), updaterUsername, other);
    }

    /**
     * Mark the entire 1:1 conversation as "deleted for me" for `me`.
     * This uses updateMany so it's fast even for big threads.
     */
    public long clearChatForUser(String me, String contact) {
        Criteria participants = new Criteria().orOperator(
                new Criteria().andOperator(where("sender").is(me),     where("receiver").is(contact)),
                new Criteria().andOperator(where("sender").is(contact), where("receiver").is(me))
        );

        Criteria notDeletedForAll = where("deletedForAll").ne(true);

        Criteria notAlreadyHiddenForMe = new Criteria().orOperator(
                where("deletedForUsers").exists(false),
                where("deletedForUsers").ne(me)
        );


        Query q = new Query(new Criteria().andOperator(
                participants, notDeletedForAll, notAlreadyHiddenForMe
        ));

        Update u = new Update().addToSet("deletedForUsers", me);

        UpdateResult res = mongoTemplate.updateMulti(q, u, MessageDocument.class);
        return res.getModifiedCount();
    }

    /** After clear: notify FE + refresh counters and recent summary */
    public void pushAfterClear(String me, String contact, long affected) {
        long now = java.time.Instant.now().getEpochSecond();

        // 1) tell the client to clear the thread view (if open)
        messagingSyncTemplate.convertAndSendToUser(
                me, "/queue/chat/cleared",
                new ChatClearedEvent(contact, affected, now)
        );

        // 2) refresh unread counters for the left pane
        sendUnreadCountUpdate(me);

        // 3) refresh "recent messages" list for the left pane
        List<RecentReceivedMessageResponse> recent = getRecentMessages(me);
        messagingSyncTemplate.convertAndSendToUser(me, "/queue/recent/summary", recent);
    }

    /**
     * Auto-deliver all messages that were SENT to this receiver while they were offline.
     * Filters out messages hidden/deleted for this user.
     */
    public void deliverAllPendingTo(String receiverUsername) {
        List<MessageDocument> pending = messageRepository
                .findByReceiverAndStatus(receiverUsername, MessageStatus.SENT)
                .stream()
                .filter(m -> m.isVisibleTo(receiverUsername))
                .toList();

        if (pending.isEmpty())
            return;

        List<String> ids = pending.stream().map(MessageDocument::getId).toList();
        // Reuse existing delivery logic (persists + notifies original senders)
        MessageStatusUpdate msUpdate = new MessageStatusUpdate();
        msUpdate.setMessageIds(ids);
        msUpdate.setStatusTimeStamp(Instant.now().getEpochSecond());
        markMessagesAsDelivered(msUpdate, receiverUsername);
    }

}
