    package com.algomeet.chatservice.controller;

    import com.algomeet.chatservice.client.GroupClient;
    import com.algomeet.chatservice.document.GroupDto;
    import com.algomeet.chatservice.document.MessageDocument;
    import com.algomeet.chatservice.document.MessageResponse;
    import com.algomeet.chatservice.dto.*;
    import com.algomeet.chatservice.dto.Member;
    import com.algomeet.chatservice.dto.clearchat.ClearChatRequest;
    import com.algomeet.chatservice.dto.clearchat.ClearChatResult;
    import com.algomeet.chatservice.dto.msgdelete.MessageDeleteRequest;
    import com.algomeet.chatservice.dto.msgdelete.MessageDeleteResult;
    import com.algomeet.chatservice.mapper.MessageMapper;
    import com.algomeet.chatservice.repository.MessageRepository;
    import com.algomeet.chatservice.service.MessageDeleteService;
    import com.algomeet.chatservice.service.MessageService;
import com.algomeet.chatservice.util.SecurityUtil;

import jakarta.validation.Valid;
    import lombok.AllArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.data.domain.Pageable;
    import org.springframework.data.domain.Sort;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.server.ResponseStatusException;

    import java.security.Principal;
    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.List;
    import java.util.stream.Collectors;

    @RestController
    @RequestMapping("/api/messages")
    @AllArgsConstructor
    @Slf4j
    public class MessageController {

        private final MessageRepository messageRepository;
        private final MessageMapper messageMapper;
        private final MessageService messageService;
        private final MessageDeleteService deleteService;
        private final GroupClient groupClient;

        @PostMapping
        public MessageDocument saveMessage(@Valid @RequestBody MessageDocument message, Principal principal) {
            return messageService.saveMessage(message, principal);
        }

        @PostMapping("/delete")
        public ResponseEntity<MessageDeleteResult> deleteMessages(@Valid @RequestBody MessageDeleteRequest req) {
            String requester = SecurityContextHolder.getContext().getAuthentication().getName();
            
            MessageDeleteResult result = deleteService.deleteMessages(req.getMessageIds(), requester, SecurityUtil.getUserKey(), req.isDeleteForEveryone());
            return ResponseEntity.ok(result);
        }

        // Optional: delete entire conversation "for me" (bulk)
        @PostMapping("/delete/conversation/{otherUser}")
        public ResponseEntity<Void> deleteConversationForMe(@PathVariable String otherUser) {
            String me = SecurityContextHolder.getContext().getAuthentication().getName();
            // load all visible messages and mark deletedForUsers add(me)
            // (implementation omitted for brevity; same pattern as single delete)
            return ResponseEntity.noContent().build();
        }

        // Get messages between two users (direct chat)
        @GetMapping("/user/{otherUser}")
        public List<MessageResponse> getDirectMessages(
                @PathVariable String otherUser,
                @RequestParam(defaultValue = "false") boolean paged,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {

            final String currentUser = getCurrentUserName(); // viewer, also one side of the convo
            final int safePage = Math.max(page, 0);
            final int pageSize = Math.min(Math.max(size, 1), 100);

            if (paged) {
                Pageable pageable = PageRequest.of(
                        safePage,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "_id"))
                );

                List<MessageDocument> docs = new ArrayList<>(
                        messageRepository.findVisibleConversation(currentUser, otherUser, currentUser, pageable)
                );
                Collections.reverse(docs);

                return docs.stream()
                        .map(messageMapper::toResponse)
                        .collect(Collectors.toList());
            } else {
                // full fetch in ascending order
                List<MessageDocument> docs = messageRepository.findVisibleConversationAll(
                        currentUser,
                        otherUser,
                        currentUser,
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

            final String currentUser = getCurrentUserName();
            final GroupDto group = groupClient.getGroupById(groupId);
            ensureGroupMembership(group, currentUser);

            final Long historyVisibleFrom = resolveHistoryVisibleFrom(group, currentUser);
            final int clampedPage = Math.max(page, 0);
            final int pageSize    = Math.min(Math.max(size, 1), 100);

            // Newest first from DB (DESC), with deterministic tie-breaker
            final Sort descSort = Sort.by(Sort.Direction.DESC, "timestamp")
                    .and(Sort.by(Sort.Direction.DESC, "_id"));

            if (paged) {
                Pageable p = PageRequest.of(clampedPage, pageSize, descSort);
                List<MessageDocument> pageDesc = messageRepository.findVisibleGroupMessages(groupId, currentUser, p);
                List<MessageDocument> pageAsc = new ArrayList<>(pageDesc);
                Collections.reverse(pageAsc);
                return toVisibleGroupResponses(pageAsc, currentUser, historyVisibleFrom);
            } else {
                Pageable p = PageRequest.of(0, 100, descSort);
                List<MessageDocument> latestDesc = messageRepository.findVisibleGroupMessages(groupId, currentUser, p);
                List<MessageDocument> latestAsc = new ArrayList<>(latestDesc);
                Collections.reverse(latestAsc);
                return toVisibleGroupResponses(latestAsc, currentUser, historyVisibleFrom);
            }
        }

        /**
         * Returns the earliest timestamp (ms) the current user may see, or null if full history is allowed.
         * History is restricted to messages sent after the member joined when sendMessageHistory is false
         * for their role.
         */
        private Long resolveHistoryVisibleFrom(GroupDto group, String currentUser) {
            if (group == null || group.getMembers() == null) return null;

            Member self = group.getMembers().stream()
                    .filter(m -> currentUser.equals(m.getUsername()))
                    .findFirst()
                    .orElse(null);

            if (self == null || self.getRole() == null || self.getMemberStartDate() == null) return null;

            RolePermissionsDto perms = group.getRolePermissions() != null
                    ? group.getRolePermissions().get(self.getRole())
                    : null;

            if (perms != null && !perms.isSendMessageHistory()) {
                return self.getMemberStartDate();
            }
            return null;
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

        @PostMapping("/mark-as-read")
        public ResponseEntity<Void> markAsRead(@Valid @RequestBody ResetUnreadRequest request) {
            messageService.markMessagesAsRead(request.getSender(), request.getReceiver());
            return ResponseEntity.noContent().build();
        }

        private String getCurrentUserName() {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth.getName(); // now returns username instead of email
        }

        private void ensureGroupMembership(GroupDto group, String username) {
            boolean isMember = group != null
                    && group.getMembers() != null
                    && group.getMembers().stream()
                    .map(Member::getUsername)
                    .anyMatch(username::equals);
            if (!isMember) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Not a member of the group");
            }
        }

        private List<MessageResponse> toVisibleGroupResponses(List<MessageDocument> docs, String currentUser, Long historyVisibleFrom) {
            return docs.stream()
                    .filter(message -> message != null && message.isVisibleTo(currentUser))
                    .filter(message -> historyVisibleFrom == null || message.getTimestamp() >= historyVisibleFrom)
                    .map(messageMapper::toResponse)
                    .collect(Collectors.toList());
        }

        @PostMapping("/clear")
        public ResponseEntity<ClearChatResult> clearChat(@RequestBody @Valid ClearChatRequest req) {
            final String me = getCurrentUserName();
            long affected = messageService.clearChatForUser(me, req.getContactId());

            log.info("Clear chat for user {} and affected {}", me, affected);

            // push real-time UI updates
            messageService.pushAfterClear(me, req.getContactId(), affected);

            return ResponseEntity.ok(new ClearChatResult(affected, req.getContactId()));
        }

        @GetMapping("/group/{groupId}/{messageId}/receipts")
        public ResponseEntity<GroupMessageReceiptResponse> getGroupReceipts(
                @PathVariable String groupId,
                @PathVariable String messageId
        ) {
            String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

            GroupDto group = groupClient.getGroupById(groupId);

            boolean member = group.getMembers()
                    .stream()
                    .anyMatch(m -> currentUser.equals(m.getUsername()));

            if (!member) {
                throw new RuntimeException("Access denied");
            }

            GroupMessageReceiptResponse response =
                    messageService.getGroupMessageReceipts(groupId, messageId);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(response);
        }

    }
