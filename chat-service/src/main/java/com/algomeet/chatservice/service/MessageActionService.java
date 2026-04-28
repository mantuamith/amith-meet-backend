package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.*;
import com.algomeet.chatservice.dto.messageactions.ForwardRequest;
import com.algomeet.chatservice.dto.messageactions.ReplyRequest;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.chatservice.dto.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        MessageDocument msg = messageRepository.findById(messageId).orElse(null);
        if (msg == null) return null;
        if (!requester.equals(msg.getSender())) return null;

        Query q = Query.query(Criteria.where("_id").is(messageId));
        Update u = new Update()
                .set("content", newContent)
                .set("metaData.isEdited", true);
        mongoTemplate.updateFirst(q, u, MessageDocument.class);

        // return updated
        MessageDocument updated = messageRepository.findById(messageId).orElse(msg);
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
        reply.setTimestamp( Instant.ofEpochSecond(req.getMsgReplyTimeStamp()));
        if (req.getGroupId() != null && !req.getGroupId().isBlank()) {
            reply.setGroupId(req.getGroupId());
        } else {
            reply.setReceiver(req.getReceiver());
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
                        ? Instant.ofEpochSecond(req.getMsgForwardTimeStamp())
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

        // ===============================
        // 🔥 GROUP READ TRACKING
        // ===============================
        messageService.initializeReadTracking(fwd);

        // ===============================
        // 🔥 SAVE + DISPATCH
        // ===============================
        MessageDocument saved = messageRepository.save(fwd);

        dispatchNewMessage(saved);

        return saved;
    }

    /* -------- Push helpers (STOMP fanout) -------- */

    public void pushMessageUpdated(String messageId) {
        messageRepository.findById(messageId).ifPresent(this::pushUpdatedToParticipants);
    }

    public void pushUpdatedToParticipants(MessageDocument doc) {
        var resp = messageMapper.toResponse(doc);
        if (doc.isGroupMessage()) {
            try {
                GroupDto group = groupClient.getGroupById(doc.getGroupId());
                for (Member member : group.members) {
                    messagingSyncTemplate.convertAndSendToUser(member.getUsername(), "/queue/update_message", resp);
                }
            } catch (Exception ignored) {}
        } else {
            if (doc.getSender() != null) {
                messagingSyncTemplate.convertAndSendToUser(doc.getSender(), "/queue/update_message", resp);
            }
            if (doc.getReceiver() != null) {
                messagingSyncTemplate.convertAndSendToUser(doc.getReceiver(), "/queue/update_message", resp);
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
