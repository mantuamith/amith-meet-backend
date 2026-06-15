package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.GroupDto;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import com.algomeet.chatservice.dto.Member;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.service.MessageDeleteService;
import com.algomeet.chatservice.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private MessageDeleteService deleteService;

    @Mock
    private GroupClient groupClient;

    @InjectMocks
    private MessageController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getGroupMessages_filtersHiddenMessagesForCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a")
        );

        GroupDto group = new GroupDto();
        group.setId("group-1");
        Member alice = new Member();
        alice.setUsername("alice");
        group.setMembers(List.of(alice));

        MessageDocument visible = new MessageDocument();
        visible.setId("m-visible");
        visible.setGroupId("group-1");
        visible.setGroupMessage(true);
        visible.setSender("bob");

        MessageDocument hidden = new MessageDocument();
        hidden.setId("m-hidden");
        hidden.setGroupId("group-1");
        hidden.setGroupMessage(true);
        hidden.setSender("alice");
        hidden.setDeletedForUsers(Set.of("alice"));

        MessageResponse visibleResponse = MessageResponse.builder()
                .id("m-visible")
                .text("hello")
                .build();

        when(groupClient.getGroupById("group-1")).thenReturn(group);
        when(messageRepository.findVisibleGroupMessages(eq("group-1"), eq("alice"), any(Pageable.class)))
                .thenReturn(List.of(hidden, visible));
        when(messageMapper.toResponse(visible)).thenReturn(visibleResponse);

        List<MessageResponse> history = controller.getGroupMessages("group-1", 0, false, 20);

        assertThat(history).containsExactly(visibleResponse);
        verify(messageMapper).toResponse(visible);
        verify(messageMapper, never()).toResponse(hidden);
    }
}
