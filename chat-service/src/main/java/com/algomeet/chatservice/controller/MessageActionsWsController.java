package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.dto.*;
import com.algomeet.chatservice.dto.messageactions.*;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.service.MessageActionService;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class MessageActionsWsController {

    private final MessageActionService actions;
    private final MessageMapper messageMapper;

    @MessageMapping("/actions/react")
    public void wsReact(@Payload ReactionCommand cmd, Principal principal) {
        actions.applyReaction(cmd.getMessageId(), cmd.getEmoji(), cmd.isAdd(), principal.getName());
        // fanout is done inside service via pushMessageUpdated(...)
    }

    @MessageMapping("/actions/pin")
    public void wsPin(@Payload PinCommand cmd, Principal principal) {
        actions.togglePin(cmd.getMessageId(), cmd.isPin(), principal.getName());
    }

    @MessageMapping("/actions/edit")
    public void wsEdit(@Payload EditMessageRequest req, Principal principal) {
        MessageDocument updated = actions.editMessage(req.getMessageId(), req.getNewContent(), principal.getName());
        // service pushes /queue/update_message to participants
    }

    @MessageMapping("/actions/reply")
    public void wsReply(@Payload ReplyRequest req, Principal principal) {
        MessageDocument saved = actions.replyTo(req, principal.getName(), null);
        // service dispatches as a new message event to participants
    }

    @MessageMapping("/actions/forward")
    public void wsForward(@Payload ForwardRequest req, Principal principal) {
        actions.forward(req, principal.getName(), null);
    }
}
