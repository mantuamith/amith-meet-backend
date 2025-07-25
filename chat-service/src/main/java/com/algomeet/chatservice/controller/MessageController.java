package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.model.Message;
import com.algomeet.chatservice.repository.MessageRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    // Save a new message
    @PostMapping
    public Message saveMessage(@Valid @RequestBody Message message) {
        message.setTimestamp(Instant.now());
        return messageRepository.save(message);
    }

    // Get messages between two users (direct chat)
    @GetMapping("/user/{user1}/{user2}")
    public List<Message> getDirectMessages(@PathVariable String user1, @PathVariable String user2,@RequestParam(defaultValue = "false") boolean paged
    ,@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (paged) {
            Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
            return messageRepository.findPagedBySenderAndReceiver(user1, user2, user2, user1, pageable);
        } else {
            return messageRepository.findTop100BySenderAndReceiverOrReceiverAndSenderOrderByTimestampDesc(user1, user2, user1, user2);
        }
    }

    // Get messages for a group (group chat)
    @GetMapping("/group/{groupId}")
    public List<Message> getGroupMessages(@PathVariable String groupId,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "false") boolean paged,
                                          @RequestParam(defaultValue = "20") int size) {
        if (paged) {
            Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "timestamp"));
            return messageRepository.findByReceiver(groupId, pageable);
        } else {
            return messageRepository.findTop100ByReceiverOrderByTimestampDesc(groupId);
        }
    }
}
