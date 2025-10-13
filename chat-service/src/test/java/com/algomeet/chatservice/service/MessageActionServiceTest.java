package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.*;
import com.algomeet.chatservice.dto.messageactions.ForwardRequest;
import com.algomeet.chatservice.dto.messageactions.ReplyRequest;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageActionServiceTest {

    @Mock private MessageRepository repo;
    @Mock private MongoTemplate mongo;
    @Mock private MessageService messageService; // for unread counters
    @Mock private SimpMessagingSyncTemplate simp;
    @Mock private GroupClient groupClient;
    @Mock private MessageMapper mapper;

    @InjectMocks private MessageActionService service;

    private MessageDocument existing;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        existing = new MessageDocument();
        existing.setId("m1");
        existing.setSender("alice");
        existing.setReceiver("bob");
        existing.setGroupId(null);
        when(repo.findById("m1")).thenReturn(Optional.of(existing));
        when(repo.findById("missing")).thenReturn(Optional.empty());
    }

    public void applyReaction(String messageId, String emoji, boolean add, String username) {
        MessageDocument msg = repo.findById(messageId).orElse(null);
        if (msg == null) return;

        String key = "metaData.reactions." + emoji;
        Query q = Query.query(Criteria.where("_id").is(messageId));
        Update u = new Update();
        if (add) u.addToSet(key, username);
        else     u.pull(key, username);
        mongo.updateFirst(q, u, MessageDocument.class);

        service.pushMessageUpdated(messageId);
    }

    @Test
    void togglePin_onlySenderCanPin() {
        // requester is sender
        service.togglePin("m1", true, "alice");
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(MessageDocument.class));

        // requester is not sender
        service.togglePin("m1", true, "charlie");
        // still only 1 call total
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(MessageDocument.class));
    }

    @Test
    void editMessage_senderOk_updatesContentAndEditedFlag() {
        when(repo.findById("m1")).thenReturn(Optional.of(existing), Optional.of(existing)); // second call after update
        MessageDocument updated = service.editMessage("m1","new text","alice");
        assertThat(updated).isNotNull();
        verify(mongo).updateFirst(any(Query.class), argThat((Update u) ->
                u.getUpdateObject().toJson().contains("\"metaData.isEdited\"")
                        && u.getUpdateObject().toJson().contains("\"content\"")
        ), eq(MessageDocument.class));
    }

    @Test
    void editMessage_nonSender_forbiddenReturnsNull() {
        MessageDocument updated = service.editMessage("m1","new text","charlie");
        assertThat(updated).isNull();
        verify(mongo, never())
                .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(MessageDocument.class));
    }

    @Test
    void replyTo_createsMessageWithReplyMeta_andDispatches() {
        when(repo.save(any(MessageDocument.class))).thenAnswer(inv -> {
            MessageDocument d = inv.getArgument(0);
            d.setId("replyId");
            return d;
        });

        ReplyRequest req = new ReplyRequest();
        req.setReplyToMessageId("orig");
        req.setReceiver("bob");
        req.setContent("reply");
        MessageDocument saved = service.replyTo(req, "alice", null);

        assertThat(saved.getId()).isEqualTo("replyId");
        assertThat(saved.getMetaData().getReplyToMessageId()).isEqualTo("orig");
        verify(simp).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any());
        verify(messageService).sendUnreadCountUpdate("bob");
    }

    @Test
    void forward_missingOriginal_returnsNull() {
        MessageDocument result = service.forward(makeForward("missing","bob", null), "alice", null);
        assertThat(result).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    void forward_toUser_copiesFields_andDispatches() {
        MessageDocument original = new MessageDocument();
        original.setId("orig1");
        original.setSender("bob");
        original.setContent("hello");
        when(repo.findById("orig1")).thenReturn(Optional.of(original));

        when(repo.save(any(MessageDocument.class))).thenAnswer(inv -> {
            MessageDocument d = inv.getArgument(0);
            d.setId("fwdId");
            return d;
        });

        MessageDocument saved = service.forward(makeForward("orig1", "charlie", null), "alice", null);
        assertThat(saved.getId()).isEqualTo("fwdId");
        assertThat(saved.getForwarded()).isNotNull();
        assertThat(saved.getForwarded().getOriginalMessageId()).isEqualTo("orig1");
        verify(simp).convertAndSendToUser(eq("charlie"), eq("/queue/messages"), any());
        verify(messageService).sendUnreadCountUpdate("charlie");
    }

    // helpers
    private ForwardRequest makeForward(String origId, String receiver, String groupId) {
        ForwardRequest fr = new ForwardRequest();
        fr.setOriginalMessageId(origId);
        fr.setReceiver(receiver);
        fr.setGroupId(groupId);
        return fr;
    }
}
