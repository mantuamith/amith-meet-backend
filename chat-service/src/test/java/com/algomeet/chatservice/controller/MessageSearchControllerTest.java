package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.dto.msgsearch.SearchMessageRequest;
import com.algomeet.chatservice.dto.msgsearch.SearchMessageResponse;
import com.algomeet.chatservice.service.MessageSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageSearchController.class)
class MessageSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MessageSearchService messageSearchService;

    @Test
    @WithMockUser(username = "viewerUser")
    @DisplayName("GET /api/messages/search passes query params and viewer from SecurityContext")
    void getSearch_ok() throws Exception {
        when(messageSearchService.search(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(SearchMessageResponse.builder().snippet("hi").build()));

        mvc.perform(get("/api/messages/search")
                        .param("q", "hello")
                        .param("otherUser", "alice")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        ArgumentCaptor<String> viewer = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> other = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> q = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);

        verify(messageSearchService).search(viewer.capture(), other.capture(), q.capture(), page.capture(), size.capture());
        assertThat(viewer.getValue()).isEqualTo("viewerUser");
        assertThat(other.getValue()).isEqualTo("alice");
        assertThat(q.getValue()).isEqualTo("hello");
        assertThat(page.getValue()).isEqualTo(2);
        assertThat(size.getValue()).isEqualTo(50);
    }

    @Test
    @WithMockUser(username = "viewerUser")
    @DisplayName("GET /api/messages/search without required 'q' -> 400")
    void getSearch_missingQ_badRequest() throws Exception {
        mvc.perform(get("/api/messages/search")
                        .param("otherUser", "alice"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "viewerUser")
    @DisplayName("GET /api/messages/search with defaults when page/size omitted")
    void getSearch_defaults_ok() throws Exception {
        when(messageSearchService.search(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mvc.perform(get("/api/messages/search")
                        .param("q", "hello"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> page = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(messageSearchService).search(anyString(), any(), anyString(), page.capture(), size.capture());
        assertThat(page.getValue()).isEqualTo(0);
        assertThat(size.getValue()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = "viewerUser")
    @DisplayName("POST /api/messages/search uses body values and defaults")
    void postSearch_ok() throws Exception {
        when(messageSearchService.search(anyString(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        String body = """
        {
          "q": "report",
          "otherUser": "bob",
          "page": 3,
          "size": 10
        }
        """;

        mvc.perform(post("/api/messages/search")
                        .with(csrf()) // <-- add this
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(messageSearchService).search(eq("viewerUser"), eq("bob"), eq("report"), eq(3), eq(10));
    }
}
