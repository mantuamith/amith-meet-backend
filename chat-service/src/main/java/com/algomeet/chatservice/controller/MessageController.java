package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MessageService messageService;

    @PostMapping
    public MessageDocument saveMessage(@Valid @RequestBody MessageDocument message, Principal principal) {
        return messageService.saveMessage(message, principal);
    }

    // Get messages between two users (direct chat)
    @GetMapping("/user/{user1}/{user2}")
    public List<MessageResponse> getDirectMessages(@PathVariable String user1, @PathVariable String user2, @RequestParam(defaultValue = "false") boolean paged
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        List<MessageDocument> docs = paged ?
                messageRepository.findPagedBySenderAndReceiver(user1, user2, user2, user1,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"))) :
                messageRepository.findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(user1, user2, user1, user2);

        return docs.stream()
                .map(messageMapper::toResponse)
                .toList();
    }

    // Get messages for a group (group chat)
    @GetMapping("/group/{groupId}")
    public List<MessageResponse> getGroupMessages(@PathVariable String groupId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "false") boolean paged,
                                                  @RequestParam(defaultValue = "20") int size) {
        List<MessageDocument> docs = paged ?
                messageRepository.findByReceiver(groupId,
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"))) :
                messageRepository.findTop100ByReceiverOrderByTimestampDesc(groupId);

        return docs.stream()
                .map(messageMapper::toResponse)
                .toList();
    }


    //API to GET  Recent Messages for a User ( all messaages from all users).
    @GetMapping("/recent")
    public List<RecentReceivedMessageResponse> getRecentMessages(@RequestParam String receiver) {
        return messageService.getRecentMessages(receiver);
    }


    // Recent unread messages API
    @GetMapping("/recent/{userId}")
    public List<MessageResponse> getRecentUnread(@PathVariable String userId) {
        return messageService.getRecentUnreadMessages(userId);
    }

    //Reset unread count API
    @PostMapping("/reset-unread")
    public void resetUnread(@RequestParam String sender, @RequestParam String receiver) {
        messageService.resetUnreadCount(sender, receiver);
    }

}
