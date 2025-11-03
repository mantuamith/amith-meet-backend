package com.algomeet.chatservice.service;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.msgsearch.SearchMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageTextSearchDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageSearchServiceTest {

    @Mock
    MessageTextSearchDao dao;

    @Mock
    MessageMapper mapper;

    @InjectMocks
    MessageSearchService service;

    private static MessageDocument doc(String id, String text) {
        MessageDocument d = new MessageDocument();
        d.setId(id);
        d.setContent(text);
        return d;
    }

    private static MessageResponse mr(String id, String text) {
        MessageResponse r = new MessageResponse();
        r.setId(id);
        r.setText(text);
        r.setContent(text);
        return r;
    }

    @Test
    @DisplayName("Maps results and returns snippet from MessageResponse.text")
    void mapsResults_basic() {
        var d1 = doc("1", "hello world");
        when(dao.searchVisibleByText(eq("viewer"), eq("other"), eq("hello"), any(Pageable.class)))
                .thenReturn(List.of(d1));
        when(mapper.toResponse(d1)).thenReturn(mr("1", "hello world"));

        List<SearchMessageResponse> out = service.search("viewer", "other", "hello", 0, 20);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getMessage().getId()).isEqualTo("1");
        assertThat(out.get(0).getSnippet()).isEqualTo("hello world");
        assertThat(out.get(0).getScore()).isNull(); // per current implementation
    }

    @Test
    @DisplayName("Clamps negative page to 0 and size to [1,100]")
    void clampsPaging() {
        when(dao.searchVisibleByText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        service.search("viewer", null, "q", -5, 0);   // size 0 -> 1
        service.search("viewer", null, "q", -1, 500); // size 500 -> 100

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(dao, times(2)).searchVisibleByText(eq("viewer"), isNull(), eq("q"), captor.capture());

        assertThat(captor.getAllValues().get(0).getPageNumber()).isEqualTo(0);
        assertThat(captor.getAllValues().get(0).getPageSize()).isEqualTo(1);

        assertThat(captor.getAllValues().get(1).getPageNumber()).isEqualTo(0);
        assertThat(captor.getAllValues().get(1).getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Handles empty result set")
    void emptyResults() {
        when(dao.searchVisibleByText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        List<SearchMessageResponse> out = service.search("viewer", "alice", "none", 1, 10);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("Passes otherUser as null and blank correctly")
    void otherUserNullOrBlank() {
        when(dao.searchVisibleByText(anyString(), any(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        service.search("viewer", null, "q", 0, 20);
        service.search("viewer", "   ", "q", 0, 20);

        verify(dao, times(2)).searchVisibleByText(eq("viewer"), any(), eq("q"), any(Pageable.class));
    }
}
