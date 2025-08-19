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

        List<MessageDocument> docs;
        if (paged) {
            Pageable p = PageRequest.of(page, Math.min(size, 100),
                    Sort.by(Sort.Direction.ASC, "timestamp"));
            docs = messageRepository
                    .findConversation(currentUser, otherUser, p)
                    .getContent();
        } else {
            docs = messageRepository
                    .findConversationAll(currentUser, otherUser,
                            Sort.by(Sort.Direction.ASC, "timestamp"))
                    .stream()
                    .toList();
        }
        return docs.stream().map(messageMapper::toResponse).toList();

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
