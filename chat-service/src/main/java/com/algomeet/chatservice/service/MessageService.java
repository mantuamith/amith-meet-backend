package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.CallMetaData;
import com.algomeet.chatservice.document.GroupDto;
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
    private final GroupClient groupClient;
    private final SimpMessagingSyncTemplate messagingSyncTemplate;
    private final MessageMapper messageMapper;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;

    // -------- RECENT / UNREAD --------
    public List<MessageResponse> getRecentUnreadMessages(String userId) {
        log.debug("[Unread] Fetching unread for user={}", userId);
        List<MessageResponse> out = loadRecentScopeMessages(userId).stream()
                .filter(m -> isUnreadForUser(m, userId))
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
        Map<String, Long> countsBySender = loadRecentScopeMessages(userId).stream()
                .filter(m -> isUnreadForUser(m, userId))
                .map(m -> resolveThreadId(m, userId))
                .filter(MessageService::hasText)
                .collect(Collectors.groupingBy(threadId -> threadId, Collectors.counting()));

        List<UnreadCountResponse> summary = countsBySender.entrySet().stream()
                .map(e -> new UnreadCountResponse(e.getKey(), e.getValue().intValue()))
                .sorted(Comparator.comparing(UnreadCountResponse::getContactId))
                .toList();

        log.info("[UnreadPush] user={} distinctContacts={} totalUnread={}",
                userId, summary.size(), countsBySender.values().stream().mapToLong(Long::longValue).sum());
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
            message.setTimestamp(System.currentTimeMillis());
        }
        initializeReadTracking(message);
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
        if (!hasText(userId)) {
            log.debug("[Recent] empty user id");
            return List.of();
        }

        log.debug("[Recent] Computing thread list for user={}", userId);

        // ✅ Fetch valid groups ONCE (IMPORTANT)
        Set<String> validGroups = fetchGroupIdsForUsername(userId);

        List<MessageDocument> all = loadRecentScopeMessages(userId);
        if (all.isEmpty()) {
            log.debug("[Recent] user={} no messages", userId);
            return List.of();
        }

        List<MessageDocument> visible = all.stream()
                .filter(m -> m.isVisibleTo(userId))
                .filter(this::isRecentThreadCandidate)
                .toList();

        if (visible.isEmpty()) return List.of();

        Map<String, List<MessageDocument>> byContact = new HashMap<>();

        for (MessageDocument m : visible) {
            String contactId = resolveThreadId(m, userId);
            if (!hasText(contactId)) continue;

            byContact.computeIfAbsent(contactId, ignored -> new ArrayList<>()).add(m);
        }

        List<RecentReceivedMessageResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<MessageDocument>> e : byContact.entrySet()) {

            String contactId = e.getKey();

            // 🔥 keep ONLY visible messages
            List<MessageDocument> thread = e.getValue().stream()
                    .filter(m -> m.isVisibleTo(userId))
                    .toList();

            // ✅ FULL CLEAR → remove thread completely
            if (thread.isEmpty()) {
                log.debug("[Recent] Thread fully cleared contact={} user={}", contactId, userId);
                continue;
            }

            boolean isGroup = thread.stream().anyMatch(MessageDocument::isGroupMessage);

            if (isGroup && !validGroups.contains(contactId)) {
                continue;
            }

            // ✅ latest ONLY from visible messages
            MessageDocument latest = thread.stream()
                    .filter(m -> m.getTimestamp() != null)
                    .max(Comparator.comparing(MessageDocument::getTimestamp))
                    .orElse(null);

            if (latest == null) continue;

            long ts = latest.getTimestamp();

            // 🔥 unread ONLY from visible messages
            int unread = (int) thread.stream()
                    .filter(m -> isUnreadForThread(m, userId, contactId))
                    .count();

            result.add(new RecentReceivedMessageResponse(
                    contactId,
                    latest.getContent(),
                    latest.getMessageMediaType(),
                    ts,
                    unread,
                    latest.getEncryptionMetadata()
            ));
        }

        List<RecentReceivedMessageResponse> out = result.stream()
                .sorted(Comparator.comparingLong(RecentReceivedMessageResponse::getTimestamp).reversed())
                .toList();

        log.debug("[Recent] user={} threads={}", userId, out.size());
        return out;
    }

    private List<MessageDocument> loadRecentScopeMessages(String userId) {
        Map<String, MessageDocument> uniqueById = new LinkedHashMap<>();

        for (MessageDocument message : messageRepository.findBySenderOrReceiver(userId, userId)) {
            if (message.getId() != null) {
                uniqueById.put(message.getId(), message);
            }
        }
        log.info("UniqueId={}", uniqueById.size());
        log.info("userId={}", userId);

        Set<String> groupIds = fetchGroupIdsForUsername(userId);
        if (!groupIds.isEmpty()) {
            for (MessageDocument message : messageRepository.findByGroupIdInOrReceiverIn(groupIds, groupIds)) {
                if (message.getId() != null) {
                    uniqueById.put(message.getId(), message);
                }
            }
        }
        log.info("UniqueId values ={}", uniqueById.values());
        return new ArrayList<>(uniqueById.values());
    }

    private Set<String> fetchGroupIdsForUsername(String userId) {
        try {
            return groupClient.getGroupsForUsername(userId).stream()
                    .map(GroupDto::getId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception ex) {
            log.warn("[Recent] Failed to fetch groups for user={}: {}", userId, ex.getMessage());
            return Set.of();
        }
    }

    public void initializeReadTracking(MessageDocument message) {
        if (message == null || !message.isGroupMessage()) {
            return;
        }
        message.markReadBy(message.getSender(), Instant.now().toEpochMilli());
    }

    private boolean isUnreadForThread(MessageDocument message, String userId, String threadId) {

        // 🔥 ADD THIS LINE
        if (!message.isVisibleTo(userId)) {
            return false;
        }

        if (!isUnreadForUser(message, userId)) {
            return false;
        }

        return threadId.equals(resolveThreadId(message, userId));
    }

    private boolean isUnreadForUser(MessageDocument message, String userId) {
        if (message == null || !hasText(userId)) {
            return false;
        }

        //ignore cleared/deleted messages
        if (!message.isVisibleTo(userId)) {
            return false;
        }

        if (message.isGroupMessage()) {
            return hasText(resolveThreadId(message, userId))
                    && !userId.equals(message.getSender())
                    && !message.isReadBy(userId);
        }
        return userId.equals(message.getReceiver())
                && message.getStatus() != MessageStatus.READ;
    }

    private String resolveThreadId(MessageDocument message, String userId) {
        if (message.isGroupMessage()) {
            return hasText(message.getGroupId()) ? message.getGroupId() : message.getReceiver();
        }
        if (userId.equals(message.getSender())) {
            return message.getReceiver();
        }
        if (userId.equals(message.getReceiver())) {
            return message.getSender();
        }
        return null;
    }

    private boolean isRecentThreadCandidate(MessageDocument message) {
        if (!hasText(message.getSender())) {
            return false;
        }
        if (message.isGroupMessage()) {
            return hasText(message.getGroupId()) || hasText(message.getReceiver());
        }
        return hasText(message.getReceiver());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isGroupMember(String groupId, String username) {
        if (!hasText(groupId) || !hasText(username)) {
            return false;
        }
        try {
            GroupDto group = groupClient.getGroupById(groupId);
            return group != null
                    && group.getMembers() != null
                    && group.getMembers().stream()
                    .map(Member::getUsername)
                    .anyMatch(username::equals);
        } catch (Exception ex) {
            log.warn("[GroupMembership] Failed group lookup groupId={} user={}: {}", groupId, username, ex.getMessage());
            return false;
        }
    }

    // -------- DELIVERY RECEIPTS --------
    public void markMessagesAsDelivered(MessageStatusUpdate deliverStatus, String receiverUsername) {
        log.info("[Delivered] receiver={} ids={}", receiverUsername, deliverStatus.getMessageIds());
        List<MessageDocument> messages = new ArrayList<>();
        for (MessageDocument message : messageRepository.findAllById(deliverStatus.getMessageIds())) {
            if (message.isGroupMessage()) {
                if (isGroupMember(message.getGroupId(), receiverUsername)
                        && !receiverUsername.equals(message.getSender())
                        && !message.isDeliveredTo(receiverUsername)) {

                    message.markDeliveredTo(receiverUsername, deliverStatus.getStatusTimeStamp());
                    if (message.getStatus() == MessageStatus.SENT) {
                        message.setStatus(MessageStatus.DELIVERED);
                    }
                    message.setMsgDeliveredTimeStamp(deliverStatus.getStatusTimeStamp());
                    messages.add(message);
                }
                continue;
            }
            if (receiverUsername.equals(message.getReceiver()) && message.getStatus() == MessageStatus.SENT) {
                message.setStatus(MessageStatus.DELIVERED);
                message.setMsgDeliveredTimeStamp(deliverStatus.getStatusTimeStamp());
                messages.add(message);
            }
        }

        if (messages.isEmpty()) {
            log.debug("[Delivered] No eligible messages to mark. receiver={} ids={}", receiverUsername, deliverStatus.getMessageIds());
            return;
        }

        messageRepository.saveAll(messages);
        long nowSec = deliverStatus.getStatusTimeStamp();
        log.info("[Delivered] Updated count={} receiver={}", messages.size(), receiverUsername);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            DeliveryReceipt receipt = new DeliveryReceipt(receiverUsername,msgs.get(0).getGroupId(), ids, nowSec);
            log.debug("[Delivered->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingSyncTemplate.convertAndSendToUser(senderId, "/queue/delivery-receipts", receipt);
        });
    }

    // -------- READ RECEIPTS --------
    public void markMessagesAsRead(MessageStatusUpdate readUpdate, String readerId) {
        log.info("[Read] reader={} ids={}", readerId, readUpdate.getMessageIds());
        List<MessageDocument> candidates = messageRepository.findAllById(readUpdate.getMessageIds());
        List<MessageDocument> messages = new ArrayList<>();
        for (MessageDocument message : candidates) {
            if (!message.isVisibleTo(readerId))
                continue;

            if (message.isGroupMessage()) {
                if (isGroupMember(message.getGroupId(), readerId)
                        && !readerId.equals(message.getSender())
                        && !message.isReadBy(readerId)) {
                    message.markReadBy(readerId,  readUpdate.getStatusTimeStamp());
                    messages.add(message);
                }
                continue;
            }
            if (readerId.equals(message.getReceiver()) && message.getStatus() != MessageStatus.READ) {
                message.setStatus(MessageStatus.READ);
                message.setMsgReadTimeStamp(readUpdate.getStatusTimeStamp());
                messages.add(message);
            }
        }

        if (messages.isEmpty()) {
            log.debug("[Read] No eligible messages to mark. reader={} ids={}", readerId, readUpdate.getMessageIds());
            return;
        }

        messageRepository.saveAll(messages);
        long nowSec = readUpdate.getStatusTimeStamp();
        log.info("[Read] Updated count={} reader={}", messages.size(), readerId);

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {
            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();
            ReadReceipt receipt = new ReadReceipt(readerId, msgs.get(0).getGroupId(), ids, nowSec);
            log.debug("[Read->Notify] toSender={} ids={} at={}", senderId, ids.size(), nowSec);
            messagingSyncTemplate.convertAndSendToUser(senderId, "/queue/read-receipts", receipt);
        });

        // keep the reader's unread badges current
        sendUnreadCountUpdate(readerId);
    }

    public void markMessagesAsRead(String reqSenderId, String contactId) {

        log.info("[Read] reader={} thread={}", reqSenderId, contactId);
        boolean isGroup = messageRepository.existsByGroupId(contactId);
        List<MessageDocument> all;
        if (isGroup) {
            all = messageRepository.findByGroupId(contactId);
        } else {
            all = messageRepository.findBySenderAndReceiver(contactId, reqSenderId);
        }

        log.info("[Read] fetched size={}", all.size());

        List<MessageDocument> messages = new ArrayList<>();

        for (MessageDocument message : all) {

            // ignore deleted/hidden messages
            if (!message.isVisibleTo(reqSenderId)) continue;

            if (message.isGroupMessage()) {

                if (isGroupMember(message.getGroupId(), reqSenderId)
                        && !reqSenderId.equals(message.getSender())
                        && !message.isReadBy(reqSenderId)) {

                    message.markReadBy(reqSenderId, Instant.now().toEpochMilli());
                    messages.add(message);
                }
                continue;
            }

            if (reqSenderId.equals(message.getReceiver())
                    && message.getStatus() != MessageStatus.READ) {

                message.setStatus(MessageStatus.READ);
                message.setMsgReadTimeStamp(Instant.now().toEpochMilli());
                messages.add(message);
            }
        }

        if (messages.isEmpty()) {
            log.debug("[Read] No eligible messages to mark. reader={} thread={}", reqSenderId, contactId);

            sendUnreadCountUpdate(reqSenderId);
            //pushRecentUpdate(reqSenderId); // IMPORTANT

            return;
        }

        messageRepository.saveAll(messages);

        long nowSec = Instant.now().getEpochSecond();
        log.info("[Read] Updated count={}", messages.size());

        Map<String, List<MessageDocument>> bySender =
                messages.stream().collect(Collectors.groupingBy(MessageDocument::getSender));

        bySender.forEach((senderId, msgs) -> {

            List<String> ids = msgs.stream().map(MessageDocument::getId).toList();

            ReadReceipt receipt = new ReadReceipt(
                    contactId,
                    msgs.get(0).getGroupId(),
                    ids,
                    nowSec
            );

            messagingSyncTemplate.convertAndSendToUser(
                    senderId,
                    "/queue/read-receipts",
                    receipt
            );
        });

        // CRITICAL
        sendUnreadCountUpdate(reqSenderId);
        //pushRecentUpdate(reqSenderId); // IMPORTANT
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

        boolean isGroup = messageRepository.existsByGroupId(contact);

        Criteria criteria;

        if (isGroup) {
            // ✅ GROUP CLEAR
            criteria = new Criteria().andOperator(
                    where("groupId").is(contact),
                    where("deletedForAll").ne(true),
                    new Criteria().orOperator(
                            where("deletedForUsers").exists(false),
                            where("deletedForUsers").ne(me)
                    )
            );
        } else {
            // ✅ DIRECT CLEAR (FIXED)

            Criteria participants = new Criteria().orOperator(
                    where("sender").is(me).and("receiver").is(contact),
                    where("sender").is(contact).and("receiver").is(me)
            );

            criteria = new Criteria().andOperator(
                    participants,
                    where("groupId").is(null), // 🔥 critical fix
                    where("deletedForAll").ne(true),
                    new Criteria().orOperator(
                            where("deletedForUsers").exists(false),
                            where("deletedForUsers").ne(me)
                    )
            );
        }

        Query q = new Query(criteria);
        Update u = new Update().addToSet("deletedForUsers", me);

        long modified = mongoTemplate.updateMulti(q, u, MessageDocument.class).getModifiedCount();

        log.info("[Clear] user={} contact={} modified={}", me, contact, modified);

        return modified;
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

    public GroupMessageReceiptResponse getGroupMessageReceipts(
            String groupId,
            String messageId
    ) {

        if (!hasText(groupId) || !hasText(messageId)) {
            return null;
        }

        MessageDocument msg = messageRepository
                .findById(messageId)
                .orElse(null);

        if (msg == null) {
            return null;
        }

        // ✅ must be group message
        if (!msg.isGroupMessage()) {
            return null;
        }

        // ✅ validate group ownership
        if (!groupId.equals(msg.getGroupId())) {
            return null;
        }

        List<UserStatus> delivered =
                msg.getDeliveredByUsers() != null
                        ? msg.getDeliveredByUsers()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(u -> u.getUsername() != null)
                        .filter(u -> !u.getUsername().equals(msg.getSender()))
                        .sorted(Comparator.comparing(UserStatus::getTimestamp))
                        .toList()
                        : List.of();

        List<UserStatus> read =
                msg.getReadByUsers() != null
                        ? msg.getReadByUsers()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(u -> u.getUsername() != null)
                        .filter(u -> !u.getUsername().equals(msg.getSender()))
                        .sorted(Comparator.comparing(UserStatus::getTimestamp))
                        .toList()
                        : List.of();

        return new GroupMessageReceiptResponse(
                groupId,
                msg.getId(),
                delivered.size(),
                read.size(),
                delivered,
                read
        );
    }

}
