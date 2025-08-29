package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.dto.MessageDeletedEvent;
import com.algomeet.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageDeleteService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Delete a message (for me or for everyone).
     * Rules:
     * - "Delete for me": any participant can add themselves to deletedForUsers.
     * - "Delete for everyone": only the sender can do it (optionally within a time window).
     */
    public void deleteMessage(String messageId, String requester, boolean forEveryone) {
        MessageDocument msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        // participant check
        boolean isParticipant =
                requester.equals(msg.getSender()) || requester.equals(msg.getReceiver());
        if (!isParticipant) {
            throw new SecurityException("Not a participant of this message");
        }

        long now = Instant.now().getEpochSecond();

        if (forEveryone) {
            // Only sender can delete for all (typical WhatsApp rule)
            if (!requester.equals(msg.getSender())) {
                throw new SecurityException("Only sender can delete for everyone");
            }
            // Optional: enforce window (e.g., within 1 hour)
            // if (now - msg.getTimestamp().getEpochSecond() > 3600) { ... }

            msg.setDeletedForAll(true);
            msg.setDeletedAt(now);
            messageRepository.save(msg);

            // notify BOTH participants (each on their user queue)
            MessageDeletedEvent evt = new MessageDeletedEvent(messageId, requester, true, now);
            messagingTemplate.convertAndSendToUser(msg.getSender(),   "/queue/message-deletes", evt);
            messagingTemplate.convertAndSendToUser(msg.getReceiver(), "/queue/message-deletes", evt);
        } else {
            // Delete only for me: add requester into deletedForUsers set
            Set<String> set = msg.getDeletedForUsers();
            if (set == null) {
                set = new java.util.HashSet<>();
                msg.setDeletedForUsers(set);
            }
            if (set.add(requester)) {
                messageRepository.save(msg);
            }
            // notify only the requester client (to update local UI if needed)
            MessageDeletedEvent evt = new MessageDeletedEvent(messageId, requester, false, now);
            messagingTemplate.convertAndSendToUser(requester, "/queue/message-deletes", evt);
        }
    }
}
