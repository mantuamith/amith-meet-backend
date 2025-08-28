package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageService;
import com.algomeet.chatservice.dto.ResetUnreadRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Collections;
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
    @GetMapping("/user/{otherUser}")
    public List<MessageResponse> getDirectMessages(@PathVariable String otherUser, @RequestParam(defaultValue = "false") boolean paged
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        String currentUser = getCurrentUserName();

        if (paged) {
            // 1) fetch newest page first (DESC)
            Pageable p = PageRequest.of(page, Math.min(size, 100),
                    Sort.by(Sort.Direction.DESC, "timestamp"));
            List<MessageDocument> content = messageRepository
                    .findConversation(currentUser, otherUser, p)
                    .getContent();

            // 2) reverse so FE gets ascending order inside the page
            Collections.reverse(content);

            return content.stream()
                    .map(messageMapper::toResponse)
                    .toList();
        } else {
            // Non-paged: get all in DESC then reverse to ASC
            List<MessageDocument> allDesc = messageRepository
                    .findConversationAll(currentUser, otherUser,
                            Sort.by(Sort.Direction.DESC, "timestamp"))
                    .stream()
                    .toList();

            Collections.reverse(allDesc);

            return allDesc.stream()
                    .map(messageMapper::toResponse)
                    .toList();
        }

    }

    // Get messages for a group (group chat)
    @GetMapping("/group/{groupId}")
    public List<MessageResponse> getGroupMessages(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "20") int size) {

        List<MessageDocument> docs;

        if (paged) {
            // Fetch newest page first
            Pageable p = PageRequest.of(page, Math.min(size, 100),
                    Sort.by(Sort.Direction.DESC, "timestamp"));
            docs = messageRepository.findByReceiver(groupId, p);

            // Return page in ascending order for UI rendering
            Collections.reverse(docs);
        } else {
            // Grab latest 100 (DESC) then present ASC
            docs = messageRepository.findTop100ByReceiverOrderByTimestampDesc(groupId);
            Collections.reverse(docs);
        }

        return docs.stream()
                .map(messageMapper::toResponse)
                .toList();
    }



    //API to GET  Recent Messages for a User ( all messaages from all users).
    @GetMapping("/recent")
    public List<RecentReceivedMessageResponse> getRecentMessages(@RequestParam String receiver) {
        return messageService.getRecentMessages(receiver);
    }


    // Recent unread messages API = TO Address
    @GetMapping("/recent/{userId}")
    public List<MessageResponse> getRecentUnread(@PathVariable String userId) {
        return messageService.getRecentUnreadMessages(userId);
    }

    //Reset unread count API
    @PostMapping("/reset-unread")
    public ResponseEntity<Void> resetUnread(@Valid @RequestBody ResetUnreadRequest request) {
        messageService.resetUnreadCount(request.getSender(), request.getReceiver());
        return ResponseEntity.noContent().build();
    }


    private String getCurrentUserName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName(); // now returns username instead of email
    }

}
