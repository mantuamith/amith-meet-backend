package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.dto.GroupDto;
import com.algomeet.chatservice.model.Message;
import com.algomeet.chatservice.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
public class ChatWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private GroupClient groupClient;

    @MessageMapping("/chat")
    public void handleChatMessage(Message message, Principal principal) {
        message.setSender(principal.getName());
        message.setTimestamp(Instant.now());

        messageRepository.save(message);

        if (message.isGroupMessage()) {
            GroupDto group = groupClient.getGroupById(Long.parseLong(message.getGroupId()));
            for (String member : group.members) {
                if (!member.equals(message.getSender())) {
                    messagingTemplate.convertAndSendToUser(member, "/queue/messages", message);
                }
            }
        } else {
            messagingTemplate.convertAndSendToUser(message.getReceiver(), "/queue/messages", message);
        }
    }

    @GetMapping("/messages/history")
    public List<Message> getChatHistory(@RequestParam String user1, @RequestParam String user2) {
        return messageRepository.findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(user1, user2, user2, user1);
    }
}
