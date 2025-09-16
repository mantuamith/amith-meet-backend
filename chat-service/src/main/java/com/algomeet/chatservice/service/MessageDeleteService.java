package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.dto.MessageDeleteResult;
import com.algomeet.chatservice.dto.MessageDeletedEvent;
import com.algomeet.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

@Service
@RequiredArgsConstructor
public class MessageDeleteService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Bulk delete. If deleteForEveryone = true → only the sender of each message can do this.
     * If false → delete only for requester (add requester to deletedForUsers).
     */
    public MessageDeleteResult deleteMessages(List<String> messageIds, String requester, boolean deleteForEveryone) {
        long now = Instant.now().getEpochSecond();

        List<String> deletedForEveryoneIds = new ArrayList<>();
        List<String> deletedForMeIds = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        // To aggregate “forEveryone” notifications: userId -> messageIds
        Map<String, List<String>> notifyForEveryoneByUser = new HashMap<>();

        // Fetch in bulk
        List<MessageDocument> docs = messageRepository.findAllById(messageIds);

        // Index by id for quick lookup / fail unknown ids
        Set<String> foundIds = docs.stream().map(MessageDocument::getId).collect(Collectors.toSet());
        for (String id : messageIds) {
            if (!foundIds.contains(id)) {
                failed.put(id, "NOT_FOUND");
            }
        }

        for (MessageDocument msg : docs) {
            String id = msg.getId();

            // participant check
            boolean isParticipant =
                    requester.equals(msg.getSender()) || requester.equals(msg.getReceiver());
            if (!isParticipant) {
                failed.put(id, "NOT_A_PARTICIPANT");
                continue;
            }

            if (TRUE.equals(msg.getDeletedForAll())) {
                // already deleted for all → nothing to do
                failed.put(id, "ALREADY_DELETED_FOR_ALL");
                continue;
            }

            if (deleteForEveryone) {
                // Only sender may delete for everyone
                if (!requester.equals(msg.getSender())) {
                    failed.put(id, "ONLY_SENDER_CAN_DELETE_FOR_EVERYONE");
                    continue;
                }
                // Optional: time window enforcement
                // if (now - msg.getTimestamp().getEpochSecond() > 3600) { failed.put(id,"WINDOW_EXPIRED"); continue; }

                msg.setDeletedForAll(true);
                msg.setDeletedAt(now);
                messageRepository.save(msg);
                deletedForEveryoneIds.add(id);

                // notify both participants of this id
                notifyForEveryoneByUser.computeIfAbsent(msg.getSender(), k -> new ArrayList<>()).add(id);
                notifyForEveryoneByUser.computeIfAbsent(msg.getReceiver(), k -> new ArrayList<>()).add(id);

            } else {
                // Delete "for me" = add requester to deletedForUsers
                Set<String> set = msg.getDeletedForUsers();
                if (set == null) {
                    set = new HashSet<>();
                    msg.setDeletedForUsers(set);
                }
                boolean changed = set.add(requester);
                if (changed) {
                    messageRepository.save(msg);
                }
                deletedForMeIds.add(id);
            }
        }

        // Push STOMP events
        if (!deletedForEveryoneIds.isEmpty()) {
            // Aggregate by user → single event per user
            for (Map.Entry<String, List<String>> e : notifyForEveryoneByUser.entrySet()) {
                MessageDeletedEvent evt = new MessageDeletedEvent(
                        e.getValue(),
                        requester,
                        true,
                       now
                );
                messagingTemplate.convertAndSendToUser(e.getKey(), "/queue/message-deletes", evt);
            }
        }

        if (!deletedForMeIds.isEmpty()) {
            // Only notify requester
            MessageDeletedEvent evt = new MessageDeletedEvent(
                    messageIds,
                    requester,
                    false,
                    now
            );
            messagingTemplate.convertAndSendToUser(requester, "/queue/message-deletes", evt);
        }

        return MessageDeleteResult.builder()
                .performedAt(now)
                .deletedForEveryone(deletedForEveryoneIds)
                .deletedForMe(deletedForMeIds)
                .failed(failed)
                .build();
    }
}

