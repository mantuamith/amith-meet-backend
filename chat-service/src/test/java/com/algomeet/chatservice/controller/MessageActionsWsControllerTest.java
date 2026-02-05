package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.config.StompUserPrincipal;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.dto.*;
import com.algomeet.chatservice.dto.messageactions.*;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.service.MessageActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageActionsWsControllerTest {

    @Mock private MessageActionService actions;
    @Mock private MessageMapper messageMapper;
    @Mock private StompUserPrincipal principal;

    @InjectMocks private MessageActionsWsController controller;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        when(principal.getName()).thenReturn("alice");
    }

    @Test
    void wsReact_callsService() {
        ReactionCommand cmd = new ReactionCommand();
        cmd.setMessageId("m1");
        cmd.setEmoji("🔥");
        cmd.setAdd(true);

        controller.wsReact(cmd, principal);
        verify(actions).applyReaction("m1", "🔥", true, "alice");
    }

    @Test
    void wsPin_callsService() {
        PinCommand cmd = new PinCommand();
        cmd.setMessageId("m2");
        cmd.setPin(true);

        controller.wsPin(cmd, principal);
        verify(actions).togglePin("m2", true, "alice");
    }

    @Test
    void wsEdit_callsService() {
        EditMessageRequest req = new EditMessageRequest();
        req.setMessageId("m3");
        req.setNewContent("edited!");

        when(actions.editMessage("m3","edited!","alice")).thenReturn(new MessageDocument());

        controller.wsEdit(req, principal);
        verify(actions).editMessage("m3","edited!","alice");
    }

    @Test
    void wsReply_callsService() {
        ReplyRequest req = new ReplyRequest();
        req.setReplyToMessageId("orig");
        req.setReceiver("bob");
        req.setContent("reply");

        when(actions.replyTo(any(ReplyRequest.class), eq("alice"), isNull())).thenReturn(new MessageDocument());

        controller.wsReply(req, principal);
        verify(actions).replyTo(any(ReplyRequest.class), eq("alice"), isNull());
    }

    @Test
    void wsForward_callsService() {
        ForwardRequest req = new ForwardRequest();
        req.setOriginalMessageId("orig2");
        req.setReceiver("bob");

        controller.wsForward(req, principal);
        verify(actions).forward(any(ForwardRequest.class), eq("alice"), isNull());
    }
}
