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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    public List<MessageResponse> getDirectMessages(
            @PathVariable String otherUser,
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        final String currentUser = getCurrentUserName();

        // clamp page >= 0, size in 1..100
        final int safePage    = Math.max(page, 0);
        final int pageSize    = Math.min(Math.max(size, 1), 100);

        if (paged) {
            // Newest page first (DESC) for fetch; then present ASC within the page
            Pageable pageable = PageRequest.of(
                    safePage,
                    pageSize,
                    Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "_id")) // deterministic
            );

            var pageResult = messageRepository.findConversation(currentUser, otherUser, pageable);

            // Reverse a *mutable* copy to present oldest->newest within this page
            List<MessageDocument> docs = new ArrayList<>(pageResult.getContent());
            Collections.reverse(docs);

            return docs.stream()
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList()); // mutable list (avoid Stream.toList())
        } else {
            // One shot full fetch in ASC order → no reverse needed
            List<MessageDocument> docs = messageRepository.findConversationAll(
                    currentUser,
                    otherUser,
                    Sort.by(Sort.Direction.ASC, "timestamp").and(Sort.by(Sort.Direction.ASC, "_id"))
            );

            return docs.stream()
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
        }
    }



    // Get messages for a group (group chat)
    @GetMapping("/group/{groupId}")
    public List<MessageResponse> getGroupMessages(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "20") int size) {

        final int clampedPage = Math.max(page, 0);
        final int pageSize    = Math.min(Math.max(size, 1), 100);

        // Newest first from DB (DESC), with deterministic tie-breaker
        final Sort descSort = Sort.by(Sort.Direction.DESC, "timestamp")
                .and(Sort.by(Sort.Direction.DESC, "_id"));

        if (paged) {
            // Get "newest page first" then flip to ASC within the page for UI
            Pageable p = PageRequest.of(clampedPage, pageSize, descSort);

            // If your repo returns List<MessageDocument>
            List<MessageDocument> pageDesc = messageRepository.findByReceiver(groupId, p);

            // If your repo returns Page<MessageDocument>, use:
            // List<MessageDocument> pageDesc = messageRepository.findByReceiver(groupId, p).getContent();

            List<MessageDocument> pageAsc = new ArrayList<>(pageDesc); // ensure mutable
            Collections.reverse(pageAsc);

            return pageAsc.stream()
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
        } else {
            // Non-paged: fetch latest 100 (DESC) then present ASC
            Pageable p = PageRequest.of(0, 100, descSort);

            // If your repo returns List<MessageDocument>
            List<MessageDocument> latestDesc = messageRepository.findByReceiver(groupId, p);

            // If your repo returns Page<MessageDocument>, use:
            // List<MessageDocument> latestDesc = messageRepository.findByReceiver(groupId, p).getContent();

            List<MessageDocument> latestAsc = new ArrayList<>(latestDesc); // mutable copy
            Collections.reverse(latestAsc);

            return latestAsc.stream()
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
        }
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
