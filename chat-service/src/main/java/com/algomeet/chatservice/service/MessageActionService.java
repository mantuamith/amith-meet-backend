package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.*;
import com.algomeet.chatservice.dto.messageactions.ForwardRequest;
import com.algomeet.chatservice.dto.messageactions.ReplyRequest;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.chatservice.dto.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
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
        if (msg == null) return;

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
        replyContent.setOriginalFrom(msg.getReceiver());
        replyContent.setOriginalMesg(msg.getContent());
        reply.setReplyContent(replyContent);
        messageService.initializeReadTracking(reply);
        MessageDocument saved = messageRepository.save(reply);
        dispatchNewMessage(saved);
        return saved;
    }

    public MessageDocument forward(ForwardRequest req, String sender, String senderKey) {
        MessageDocument original = messageRepository.findById(req.getOriginalMessageId()).orElse(null);
        if (original == null) return null;

        MessageDocument fwd = new MessageDocument();
        fwd.setTimestamp(Instant.ofEpochSecond(req.getMsgForwardTimeStamp()));
        fwd.setClientMessageId(req.getClientMessageId());
        fwd.setSender(sender);
        fwd.setSenderKey(senderKey);
        fwd.setContent(req.getContent());
        fwd.setType(original.getType());
        fwd.setMessageMediaType(original.getMessageMediaType());
        fwd.setMediaGroup(original.getMediaGroup());

        if (req.getGroupId() != null && !req.getGroupId().isBlank()) {
            fwd.setGroupId(req.getGroupId());
        } else {
            fwd.setReceiver(req.getReceiver());
            fwd.setReceiverKey(req.getToKey());
        }

        ForwardInfo fi = new ForwardInfo();
        fi.setForwarded(true);
        fi.setOriginalFrom(original.getSender());
        fi.setOriginalMessageId(original.getId());
        fi.setForwardedAt(req.getMsgForwardTimeStamp());
        fwd.setForwarded(fi);

        messageService.initializeReadTracking(fwd);
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
                GroupDto group = groupClient.getGroupById(Long.parseLong(doc.getGroupId()));
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
        var resp = messageMapper.toResponse(doc);
		                
        if (doc.isGroupMessage()) {
            try {
                
                GroupDto group = groupClient.getGroupById(Long.parseLong(doc.getGroupId()));
                // If a message includes media files, grant media access permissions to the
                // message recipients.
                mediaService.share(doc, group);
        		
                for (Member member : group.members) {
                    if (!member.equals(doc.getSender())) {
                        messagingSyncTemplate.convertAndSendToUser(member.getUsername(), "/queue/messages", resp);
                        messageService.sendUnreadCountUpdate(member.getUsername());
                    }
                }
            } catch (Exception ignored) {

            }
        } else {
        	// If a message includes media files, grant media access permissions to the
            // message recipients.
            mediaService.share(doc);
            
            // receiver side
            messagingSyncTemplate.convertAndSendToUser(doc.getReceiver(), "/queue/messages", resp);
            messageService.sendUnreadCountUpdate(doc.getReceiver());
            int unread = messageService.getUnreadCountFor(doc.getReceiver(), doc.getSender());
            messagingSyncTemplate.convertAndSendToUser(
                    doc.getReceiver(),
                    "/queue/unread/contact",
                    new UnreadCountResponse(doc.getSender(), unread)
            );
            // sender side
            messagingSyncTemplate.convertAndSendToUser(doc.getSender(), "/queue/update_message", resp);
            messageService.sendUnreadCountUpdate(doc.getSender());
            int unreadForSender = messageService.getUnreadCountFor(doc.getSender(), doc.getReceiver());
            messagingSyncTemplate.convertAndSendToUser(
                    doc.getSender(),
                    "/queue/unread/contact",
                    new UnreadCountResponse(doc.getReceiver(), unreadForSender)
            );
        }
    }
}
