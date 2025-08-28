package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageService;
import com.algomeet.chatservice.dto.ResetUnreadRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ListIterator;
import java.util.stream.Collectors;
@Slf4j
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

        final long t0 = System.nanoTime();
        final String currentUser = getCurrentUserName();

        int originalPage = page;
        int originalSize = size;

        // clamp page >= 0, size in 1..100
        final int safePage = Math.max(page, 0);
        final int pageSize = Math.min(Math.max(size, 1), 100);

        if (originalPage != safePage || originalSize != pageSize) {
            log.warn("DM list clamp applied: user={} otherUser={} paged={} reqPage={} reqSize={} -> safePage={} safeSize={}",
                    currentUser, otherUser, paged, originalPage, originalSize, safePage, pageSize);
        } else {
            log.debug("DM list request: user={} otherUser={} paged={} page={} size={}",
                    currentUser, otherUser, paged, safePage, pageSize);
        }

        try {
            if (paged) {
                // Fetch newest first (DESC); present ASC within the page by iterating backwards
                Pageable pageable = PageRequest.of(
                        safePage,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "timestamp")
                                .and(Sort.by(Sort.Direction.DESC, "_id")) // deterministic
                );
                log.trace("DM pageable built: {}", pageable);

                var pageResult = messageRepository.findConversation(currentUser, otherUser, pageable);
                var content = pageResult.getContent();

                log.debug("DM paged fetch: user={} otherUser={} page={} size={} contentSize={} hasNext={} hasPrev={}",
                        currentUser, otherUser, safePage, pageSize, content.size(),
                        pageResult.hasNext(), pageResult.hasPrevious());

                // Build ASC without mutating content
                List<MessageResponse> out = new ArrayList<>(content.size());
                for (ListIterator<MessageDocument> it = content.listIterator(content.size()); it.hasPrevious(); ) {
                    out.add(messageMapper.toResponse(it.previous()));
                }

                log.info("DM paged response: user={} otherUser={} page={} size={} outSize={} tookMs={}",
                        currentUser, otherUser, safePage, pageSize, out.size(),
                        (System.nanoTime() - t0) / 1_000_000);

                return out;

            } else {
                // One-shot full fetch in ASC order (no reordering)
                Sort sortAsc = Sort.by(Sort.Direction.ASC, "timestamp")
                        .and(Sort.by(Sort.Direction.ASC, "_id"));
                var docs = messageRepository.findConversationAll(currentUser, otherUser, sortAsc);

                log.debug("DM full fetch: user={} otherUser={} sort={} fetched={}",
                        currentUser, otherUser, sortAsc, docs.size());

                List<MessageResponse> out = docs.stream()
                        .map(messageMapper::toResponse)
                        .collect(Collectors.toList()); // mutable list

                log.info("DM full response: user={} otherUser={} outSize={} tookMs={}",
                        currentUser, otherUser, out.size(),
                        (System.nanoTime() - t0) / 1_000_000);

                return out;
            }
        } catch (RuntimeException ex) {
            log.error("DM fetch error: user={} otherUser={} paged={} page={} size={} msg={}",
                    currentUser, otherUser, paged, safePage, pageSize, ex.getMessage(), ex);
            throw ex;
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
