package com.algomeet.contactservice.controller;

import com.algomeet.contactservice.dto.UserDto;
import com.algomeet.contactservice.enums.ResponseCode;
import com.algomeet.contactservice.i18n.MessageResolver;
import com.algomeet.contactservice.service.ContactService;
import com.algomeet.contactservice.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ContactController.class)
@AutoConfigureMockMvc(addFilters = false) // unit test the controller; skip security filter chain
@Import(GlobalExceptionHandler.class)     // consistent error envelope from your advice
class ContactControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private ContactService contactService;
    @MockBean private MessageResolver i18n;

    private UsernamePasswordAuthenticationToken authWithUserKey;
    private UsernamePasswordAuthenticationToken authNoDetails;

    private static UsernamePasswordAuthenticationToken auth(String name, Map<String, Object> details) {
        var t = new UsernamePasswordAuthenticationToken(name, null, List.of());
        t.setDetails(details);
        return t;
    }

    @BeforeEach
    void setUp() {
        // Authentication with user_key in details (mimics JwtAuthenticationFilter)
        authWithUserKey = auth("user@example.com", Map.of("user_key", UUID.randomUUID().toString()));
        // Authentication without details -> simulate missing user_key
        authNoDetails  = auth("user@example.com", null);

        // ---- i18n stubbing (very important: stub both overloads explicitly) ----
        // For String message keys (success.*), echo back the key so tests can assert on it.
        when(i18n.msg(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class));

        // For ResponseCode (errors), return a stable string (default i18n key). You can also return rc.getCode().
        when(i18n.msg(any(ResponseCode.class)))
                .thenAnswer(inv -> inv.getArgument(0, ResponseCode.class).getDefaultMsgKey());
    }

    @Test
    void sendContactRequest_ok() throws Exception {
        mvc.perform(post("/api/contacts/request")
                        .param("receiverId", "friend@example.com")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.request_sent"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(contactService).sendContactRequest(any(), eq("friend@example.com"));
    }

    @Test
    void acceptContactRequest_unauthorized_whenNoAuth() throws Exception {
        mvc.perform(post("/api/contacts/accept").param("senderId", "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
    }

    @Test
    void acceptContactRequest_ok() throws Exception {
        mvc.perform(post("/api/contacts/accept")
                        .param("senderId", "friend@example.com")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.accepted"));

        verify(contactService).acceptContactRequest(eq("user@example.com"), eq("friend@example.com"));
    }

    @Test
    void getAcceptedContacts_unauthorized_whenNoUserKey() throws Exception {
        mvc.perform(get("/api/contacts")
                        .with(req -> { req.setUserPrincipal(authNoDetails); return req; }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
    }

    @Test
    void getAcceptedContacts_ok() throws Exception {
        var list = List.of(new UserDto());
        when(contactService.getContactList(any())).thenReturn(list);

        // capture UUID passed down
        ArgumentCaptor<UUID> cap = ArgumentCaptor.forClass(UUID.class);

        mvc.perform(get("/api/contacts")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.list"))
                .andExpect(jsonPath("$.data").isArray());

        verify(contactService).getContactList(cap.capture());
        assertThat(cap.getValue()).isNotNull();
    }

    @Test
    void getPendingRequests_ok() throws Exception {
        when(contactService.getPendingRequests(any())).thenReturn(List.of());

        mvc.perform(get("/api/contacts/pending-requests")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.pending_list"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void deleteContact_ok() throws Exception {
        mvc.perform(delete("/api/contacts/remove")
                        .param("contactUserId", "friend@example.com")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.removed"));

        verify(contactService).deleteContact(eq("user@example.com"), eq("friend@example.com"));
    }

    @Test
    void deleteContact_unauthorized_whenNoAuth() throws Exception {
        mvc.perform(delete("/api/contacts/remove")
                        .param("contactUserId", "friend@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
    }

    @Test
    void searchUsers_badRequest_whenBlankQuery() throws Exception {
        mvc.perform(get("/api/contacts/search")
                        .param("query", " ")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void searchUsers_ok() throws Exception {
        when(contactService.searchUsers(eq("alice"), any())).thenReturn(List.of());
        mvc.perform(get("/api/contacts/search")
                        .param("query", "alice")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.search"));
    }

    @Test
    void rejectContactRequest_ok() throws Exception {
        mvc.perform(post("/api/contacts/reject")
                        .param("senderId", "friend@example.com")
                        .with(req -> { req.setUserPrincipal(authWithUserKey); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success.contact.rejected"));

        verify(contactService).rejectContactRequest(eq("user@example.com"), eq("friend@example.com"));
    }
}
