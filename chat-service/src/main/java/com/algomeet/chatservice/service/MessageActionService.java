package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.*;
import com.algomeet.chatservice.dto.messageactions.ForwardRequest;
import com.algomeet.chatservice.dto.messageactions.ReplyRequest;
import com.algomeet.chatservice.exception.MessageEditException;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.chatservice.dto.*;

import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageActionService {

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageService messageService; // reuse unread counters & helpers
    private final SimpMessagingSyncTemplate messagingSyncTemplate;
    private final GroupClient groupClient;
    private final MessageMapper messageMapper;
    private final MediaService mediaService;

    /* -------- Core mutations -------- */

    public void applyReaction(String messageId, String emoji, boolean add, String username) {
        MessageDocument msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null || !isParticipant(msg, username)) return;

        String key = "metaData.reactions." + emoji;
        Query q = Query.query(Criteria.where("_id").is(messageId));
        Update u = new Update();
        if (add) u.addToSet(key, username);
        else     u.pull(key, username);
        mongoTemplate.updateFirst(q, u, MessageDocument.class);

        pushMessageUpdated(messageId);
    }

    public void togglePin(String messageId, boolean pin, String requester) {
        MessageDocument msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null) return;
        // Simple rule: only sender can pin/unpin; extend with role checks if needed.
        if (!requester.equals(msg.getSender())) return;

        Query q = Query.query(Criteria.where("_id").is(messageId));
        Update u = new Update().set("metaData.isPinned", pin);
        mongoTemplate.updateFirst(q, u, MessageDocument.class);

        pushMessageUpdated(messageId);
    }

    public MessageDocument editMessage(String messageId, String newContent, String requester) {

        // 1. Validate content
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new MessageEditException("Invalid content", HttpStatus.BAD_REQUEST);
        }
        newContent = newContent.trim();

        // 2. Fetch message (needed for read logic)
        MessageDocument msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null){
            throw new MessageEditException("Message not found", HttpStatus.NOT_FOUND);
        }

        // 3. Ownership
        if (!requester.equals(msg.getSender())) {
            throw new MessageEditException("Not allowed to edit", HttpStatus.FORBIDDEN);
        }

        // 4. Avoid unnecessary update
        if (newContent.equals(msg.getContent())) return msg;

        // 5. Deleted checks
        if (Boolean.TRUE.equals(msg.getDeletedForAll()))
            return null;

        if (!msg.isVisibleTo(requester)) return null;

        // 6. Time check
        Instant now = Instant.now();
        if (msg.getTimestamp().plusSeconds(300).isBefore(Instant.now())) {
            throw new MessageEditException("Edit time expired", HttpStatus.CONFLICT);
        }

        // 7. Read check (DM + GROUP SAFE)
        if (msg.isGroupMessage()) {
            if (msg.getReadByUsers() != null &&
                    msg.getReadByUsers().stream().anyMatch(u -> !u.equals(msg.getSender()))) {
                return null;
            }
        } else {
            if (msg.isReadBy(msg.getReceiver())) {
                throw new MessageEditException("Message already read", HttpStatus.CONFLICT);
            }
        }

        // 8. Ensure metadata exists
        if (msg.getMetaData() == null) {
            msg.setMetaData(new MessageMetaData());
        }

        // 9. Atomic update (SAFE)
        Query q = Query.query(
                Criteria.where("_id").is(messageId)
                        .and("sender").is(requester)
        );

        Update u = new Update()
                .set("content", newContent)
                .set("metaData.isEdited", true)
                .set("metaData.editedAt", System.currentTimeMillis());

        MessageDocument updated = mongoTemplate.findAndModify(
                q,
                u,
                FindAndModifyOptions.options().returnNew(true),
                MessageDocument.class
        );

        if (updated == null) return null;

        // 10. Push update
        pushUpdatedToParticipants(updated);

        return updated;
    }

    public MessageDocument replyTo(ReplyRequest req, String sender, String senderKey) {
        MessageDocument reply = new MessageDocument();
        reply.setSender(sender);
        reply.setSenderKey(senderKey);
        reply.setContent(req.getContent());
        reply.setStatus(MessageStatus.SENT);
        reply.setClientMessageId(req.getClientMessageId());
        reply.setTimestamp(Instant.ofEpochSecond(req.getMsgReplyTimeStamp()));
        if (req.getGroupId() != null && !req.getGroupId().isBlank()) {
            reply.setGroupId(req.getGroupId());
            reply.setGroupMessage(true);
            reply.setType(MessageType.GROUP);
        } else {
            reply.setReceiver(req.getReceiver());
            reply.setGroupMessage(false);
            reply.setType(MessageType.DIRECT);
        }
        MessageDocument msg = messageRepository.findById(req.getReplyToMessageId()).orElse(null);
        ReplyContent replyContent = new ReplyContent();
        replyContent.setOriginalMessageId(req.getReplyToMessageId());
        replyContent.setOriginalFrom(msg != null ? msg.getSender() : null);
        replyContent.setOriginalMesg(msg != null ? msg.getContent() : null);
        reply.setReplyContent(replyContent);
        messageService.initializeReadTracking(reply);
        MessageDocument saved = messageRepository.save(reply);
        dispatchNewMessage(saved);
        return saved;
    }

    public void forwardBatch(
            List<ForwardRequest>  req,
            String sender,
            String senderKey
    ) {

        if (req == null  || req.isEmpty()) {
            return;
        }

        List<ForwardRequest> ordered = req
                .stream()
                .sorted(Comparator.comparing(
                        ForwardRequest::getSequence,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        for (ForwardRequest item : ordered) {

            MessageDocument saved = forward(item, sender, senderKey);

            if (saved != null) {
                dispatchNewMessage(saved);
            }
        }
    }

    public MessageDocument forward(ForwardRequest req, String sender, String senderKey) {

        // ===============================
        // 🔥 BASIC VALIDATION
        // ===============================
        if (req == null || req.getOriginalMessageId() == null) {
            log.error("[Forward] Invalid request");
            return null;
        }

        boolean toGroup = req.getGroupId() != null && !req.getGroupId().isBlank();
        boolean toUser = req.getReceiver() != null && !req.getReceiver().isBlank();

        if (!toGroup && !toUser) {
            log.error("[Forward] Either groupId or receiver must be provided");
            return null;
        }

        MessageDocument original = messageRepository
                .findById(req.getOriginalMessageId())
                .orElse(null);

        if (original == null) {
            log.error("[Forward] Original message not found id={}", req.getOriginalMessageId());
            return null;
        }

        // ===============================
        // 🔥 DESTINATION VALIDATION (BEFORE SAVE)
        // ===============================
        if (toGroup) {
            try {
                GroupDto group = groupClient.getGroupById(req.getGroupId());

                if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
                    log.error("[Forward] Invalid or empty group groupId={}", req.getGroupId());
                    return null;
                }

                boolean senderInGroup = group.getMembers().stream()
                        .anyMatch(m -> sender.equals(m.getUsername()));

                if (!senderInGroup) {
                    log.error("[Forward] Sender {} not part of group {}", sender, req.getGroupId());
                    return null;
                }

            } catch (Exception ex) {
                log.error("[Forward] Group validation failed groupId={} error={}", req.getGroupId(), ex.getMessage());
                return null;
            }
        }

        if (toUser) {
            if (req.getReceiver() == null || req.getReceiver().isBlank()) {
                log.error("[Forward] Invalid receiver");
                return null;
            }
        }

        // ===============================
        // 🔥 BUILD FORWARDED MESSAGE
        // ===============================
        MessageDocument fwd = new MessageDocument();

        fwd.setTimestamp(
                req.getMsgForwardTimeStamp() != null
                        ? Instant.ofEpochMilli(req.getMsgForwardTimeStamp())
                        : Instant.now()
        );

        fwd.setClientMessageId(req.getClientMessageId());
        fwd.setSender(sender);
        fwd.setSenderKey(senderKey);

        // content
        fwd.setContent(req.getContent() != null ? req.getContent() : original.getContent());
        fwd.setMessageMediaType(original.getMessageMediaType());
        fwd.setMediaGroup(original.getMediaGroup());

        // encryption metadata
        fwd.setEncryptionMetadata(
                req.getEncryptionMetadata() != null
                        ? req.getEncryptionMetadata()
                        : original.getEncryptionMetadata()
        );

        // ===============================
        // 🔥 DESTINATION RESOLUTION
        // ===============================
        if (toGroup) {
            fwd.setType(MessageType.GROUP);
            fwd.setGroupId(req.getGroupId());
            fwd.setGroupMessage(true);

            fwd.setReceiver(null);
            fwd.setReceiverKey(null);

        } else {
            fwd.setType(MessageType.DIRECT);
            fwd.setReceiver(req.getReceiver());
            fwd.setReceiverKey(req.getToKey());
            fwd.setGroupMessage(false);

            fwd.setGroupId(null);
        }

        // ===============================
        // 🔥 FORWARD METADATA
        // ===============================
        ForwardInfo fi = new ForwardInfo();
        fi.setForwarded(true);
        fi.setOriginalFrom(original.getSender());
        fi.setOriginalMessageId(original.getId());
        fi.setForwardedAt(
                req.getMsgForwardTimeStamp() != null
                        ? req.getMsgForwardTimeStamp()
                        : Instant.now().getEpochSecond()
        );

        fwd.setForwarded(fi);
        fwd.setSequence(req.getSequence());
        fwd.setStatus(MessageStatus.SENT);
        // ===============================
        // 🔥 GROUP READ TRACKING
        // ===============================
        messageService.initializeReadTracking(fwd);

        // ===============================
        // 🔥 SAVE + DISPATCH
        // ===============================

        //dispatchNewMessage(saved);

        return messageRepository.save(fwd);
    }

    /* -------- Push helpers (STOMP fanout) -------- */

    public void pushMessageUpdated(String messageId) {
        messageRepository.findById(messageId).ifPresent(this::pushUpdatedToParticipants);
    }

    public void pushUpdatedToParticipants(MessageDocument doc) {

        if (doc == null) return;

        var resp = messageMapper.toResponse(doc);

        // OPTIONAL (recommended)
        // resp.setEventType("EDIT");

        if (doc.isGroupMessage()) {
            try {
                GroupDto group = groupClient.getGroupById(doc.getGroupId());

                if (group == null || group.getMembers() == null) {
                    log.error("[PushUpdate] Invalid group for id={}", doc.getId());
                    return;
                }

                for (Member member : group.getMembers()) {

                    if (member == null || member.getUsername() == null) continue;

                    // Skip users who deleted message
                    if (doc.getDeletedForUsers() != null &&
                            doc.getDeletedForUsers().contains(member.getUsername())) {
                        continue;
                    }

                    messagingSyncTemplate.convertAndSendToUser(
                            member.getUsername(),
                            "/queue/update_message",
                            resp
                    );
                }

            } catch (Exception ex) {
                log.error("[PushUpdate ERROR] groupId={} error={}", doc.getGroupId(), ex.getMessage(), ex);
            }

        } else {

            // Sender
            if (doc.getSender() != null &&
                    (doc.getDeletedForUsers() == null || !doc.getDeletedForUsers().contains(doc.getSender()))) {

                messagingSyncTemplate.convertAndSendToUser(
                        doc.getSender(),
                        "/queue/update_message",
                        resp
                );
            }

            // Receiver
            if (doc.getReceiver() != null &&
                    (doc.getDeletedForUsers() == null || !doc.getDeletedForUsers().contains(doc.getReceiver()))) {

                messagingSyncTemplate.convertAndSendToUser(
                        doc.getReceiver(),
                        "/queue/update_message",
                        resp
                );
            }
        }
    }

    public void dispatchNewMessage(MessageDocument doc) {

        if (doc == null) return;

        var resp = messageMapper.toResponse(doc);

        try {

            // ===============================
            // 🔥 GROUP MESSAGE FLOW
            // ===============================
            if (doc.isGroupMessage()) {

                if (doc.getGroupId() == null || doc.getGroupId().isBlank()) {
                    log.error("[Dispatch] Missing groupId for group message id={}", doc.getId());
                    return;
                }

                GroupDto group = groupClient.getGroupById(doc.getGroupId());

                // ✅ HARD SAFETY (VERY IMPORTANT)
                if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
                    log.error("[Dispatch] Invalid group or no members groupId={}", doc.getGroupId());
                    return;
                }

                // ✅ ensure sender is still part of group
                boolean senderInGroup = group.getMembers().stream()
                        .anyMatch(m -> doc.getSender().equals(m.getUsername()));

                if (!senderInGroup) {
                    log.warn("[Dispatch] Sender {} not part of group {}", doc.getSender(), doc.getGroupId());
                    return;
                }

                // ✅ MEDIA SHARING
                mediaService.share(doc, group);

                // ✅ SEND BACK TO SENDER (UI sync)
                messagingSyncTemplate.convertAndSendToUser(
                        doc.getSender(),
                        "/queue/update_message",
                        resp
                );

                // ✅ SEND TO ALL MEMBERS
                for (Member member : group.getMembers()) {

                    if (member == null || member.getUsername() == null) continue;

                    if (!member.getUsername().equals(doc.getSender())) {

                        messagingSyncTemplate.convertAndSendToUser(
                                member.getUsername(),
                                "/queue/messages",
                                resp
                        );

                        // 🔥 unread update per member
                        messageService.sendUnreadCountUpdate(member.getUsername());
                    }
                }

                return;
            }

            // ===============================
            // 🔥 DIRECT MESSAGE FLOW
            // ===============================
            if (doc.getReceiver() == null || doc.getReceiver().isBlank()) {
                log.error("[Dispatch] Missing receiver for direct message id={}", doc.getId());
                return;
            }

            // ✅ MEDIA SHARING
            mediaService.share(doc);

            // ✅ RECEIVER SIDE
            messagingSyncTemplate.convertAndSendToUser(
                    doc.getReceiver(),
                    "/queue/messages",
                    resp
            );

            messageService.sendUnreadCountUpdate(doc.getReceiver());

            int unread = messageService.getUnreadCountFor(doc.getReceiver(), doc.getSender());

            messagingSyncTemplate.convertAndSendToUser(
                    doc.getReceiver(),
                    "/queue/unread/contact",
                    new UnreadCountResponse(doc.getSender(), unread)
            );

            // ✅ SENDER SIDE (sync)
            messagingSyncTemplate.convertAndSendToUser(
                    doc.getSender(),
                    "/queue/update_message",
                    resp
            );

            messageService.sendUnreadCountUpdate(doc.getSender());

            int unreadForSender = messageService.getUnreadCountFor(doc.getSender(), doc.getReceiver());

            messagingSyncTemplate.convertAndSendToUser(
                    doc.getSender(),
                    "/queue/unread/contact",
                    new UnreadCountResponse(doc.getReceiver(), unreadForSender)
            );

        } catch (Exception ex) {
            log.error("[Dispatch ERROR] id={} error={}", doc.getId(), ex.getMessage(), ex);
        }
    }

    private boolean isParticipant(MessageDocument message, String username) {
        if (message == null || username == null || username.isBlank()) {
            return false;
        }
        if (message.isGroupMessage()) {
            try {
                GroupDto group = groupClient.getGroupById(message.getGroupId());
                return group != null
                        && group.members != null
                        && group.members.stream()
                        .map(Member::getUsername)
                        .anyMatch(username::equals);
            } catch (Exception ignored) {
                return false;
            }
        }
        return username.equals(message.getSender()) || username.equals(message.getReceiver());
    }
}
