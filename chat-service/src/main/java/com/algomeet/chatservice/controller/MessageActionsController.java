package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.*;
import com.algomeet.chatservice.dto.messageactions.*;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.service.MessageActionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Validated
public class MessageActionsController {

    private final MessageActionService actions;
    private final MessageMapper messageMapper;

    private String currentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping("/react-message")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void react(@Valid @RequestBody ReactionCommand cmd) {
        actions.applyReaction(cmd.getMessageId(), cmd.getEmoji(), cmd.isAdd(), currentUser());

    }

    @PostMapping("/pin-message")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pin(@Valid @RequestBody PinCommand cmd) {
        actions.togglePin(cmd.getMessageId(), cmd.isPin(), currentUser());

    }

    @PostMapping("/edit-message")
    public ResponseEntity<MessageResponse> edit(@Valid @RequestBody EditMessageRequest req) {
        MessageDocument updated = actions.editMessage(req.getMessageId(), req.getNewContent(), currentUser());
        if (updated == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(messageMapper.toResponse(updated));
    }

    @PostMapping("/reply-message")
    public ResponseEntity<MessageResponse> reply(@Valid @RequestBody ReplyRequest req) {
        MessageDocument saved = actions.replyTo(req, currentUser());
        return ResponseEntity.ok(messageMapper.toResponse(saved));
    }

    @PostMapping("/forward-message")
    public ResponseEntity<MessageResponse> forward(@Valid @RequestBody ForwardRequest req) {
        MessageDocument saved = actions.forward(req, currentUser());
        if (saved == null)
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(messageMapper.toResponse(saved));
    }
}
