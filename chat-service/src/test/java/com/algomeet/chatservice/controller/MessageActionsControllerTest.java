package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.messageactions.*;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.service.MessageActionService;
import com.algomeet.chatservice.config.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageActionsController.class)
@AutoConfigureMockMvc
class MessageActionsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MessageActionService actions;
    @MockBean MessageMapper messageMapper;

    // Security filter is referenced in your SecurityConfig; mock it so the context loads
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void letRequestsPassThroughJwtFilter() throws Exception {
        doAnswer(inv -> {
            HttpServletRequest req  = inv.getArgument(0);
            HttpServletResponse res = inv.getArgument(1);
            FilterChain chain       = inv.getArgument(2);
            chain.doFilter(req, res);            // <- critical
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "alice")
    void react_add_shouldReturn204() throws Exception {
        ReactionCommand cmd = new ReactionCommand();
        cmd.setMessageId("m1");
        cmd.setEmoji("👍");
        cmd.setAdd(true);

        mockMvc.perform(post("/api/messages/react-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cmd)))
            .andExpect(status().isNoContent());

        Mockito.verify(actions).applyReaction("m1","👍",true,"alice");
    }

    @Test
    @WithMockUser(username = "alice")
    void pin_shouldReturn204() throws Exception {
        PinCommand cmd = new PinCommand();
        cmd.setMessageId("m2");
        cmd.setPin(true);

        mockMvc.perform(post("/api/messages/pin-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cmd)))
            .andExpect(status().isNoContent());

        Mockito.verify(actions).togglePin("m2", true, "alice");
    }

    @Test
    @WithMockUser(username = "alice")
    void edit_success_shouldReturn200WithBody() throws Exception {
        EditMessageRequest req = new EditMessageRequest();
        req.setMessageId("m3");
        req.setNewContent("edited!");

        MessageDocument updated = new MessageDocument();
        updated.setId("m3");
        updated.setContent("edited!");

        MessageResponse resp = new MessageResponse();
        resp.setId("m3");
        resp.setContent("edited!");

        when(actions.editMessage(eq("m3"), eq("edited!"), eq("alice"))).thenReturn(updated);
        when(messageMapper.toResponse(updated)).thenReturn(resp);

        mockMvc.perform(post("/api/messages/edit-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("m3"))
            .andExpect(jsonPath("$.content").value("edited!"));
    }

    @Test
    @WithMockUser(username = "alice")
    void edit_forbidden_shouldReturn403() throws Exception {
        EditMessageRequest req = new EditMessageRequest();
        req.setMessageId("m4");
        req.setNewContent("nope");

        when(actions.editMessage(eq("m4"), eq("nope"), eq("alice"))).thenReturn(null);

        mockMvc.perform(post("/api/messages/edit-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice")
    void reply_shouldReturn200WithBody() throws Exception {
        ReplyRequest req = new ReplyRequest();
        req.setReplyToMessageId("orig1");
        req.setReceiver("bob");
        req.setContent("reply text");

        MessageDocument saved = new MessageDocument();
        saved.setId("newReplyId");
        saved.setContent("reply text");

        MessageResponse resp = new MessageResponse();
        resp.setId("newReplyId");
        resp.setContent("reply text");

        when(actions.replyTo(any(ReplyRequest.class), eq("alice"))).thenReturn(saved);
        when(messageMapper.toResponse(saved)).thenReturn(resp);

        mockMvc.perform(post("/api/messages/reply-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("newReplyId"))
            .andExpect(jsonPath("$.content").value("reply text"));
    }

    @Test
    @WithMockUser(username = "alice")
    void forward_success_shouldReturn200WithBody() throws Exception {
        ForwardRequest req = new ForwardRequest();
        req.setOriginalMessageId("orig2");
        req.setReceiver("bob");

        MessageDocument saved = new MessageDocument();
        saved.setId("fwdId");

        MessageResponse resp = new MessageResponse();
        resp.setId("fwdId");

        when(actions.forward(any(ForwardRequest.class), eq("alice"))).thenReturn(saved);
        when(messageMapper.toResponse(saved)).thenReturn(resp);

        mockMvc.perform(
                        post("/api/messages/forward-message")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)     // <— add accept to drive JSON
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("fwdId"));
    }

    @Test
    @WithMockUser(username = "alice")
    void forward_badRequest_whenOriginalMissing() throws Exception {
        ForwardRequest req = new ForwardRequest();
        req.setOriginalMessageId("missing");
        req.setReceiver("bob");

        when(actions.forward(any(ForwardRequest.class), eq("alice"))).thenReturn(null);

        mockMvc.perform(post("/api/messages/forward-message")
                        .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }
}
