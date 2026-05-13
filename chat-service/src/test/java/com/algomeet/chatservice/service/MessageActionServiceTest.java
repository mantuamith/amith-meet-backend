package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.*;
import com.algomeet.chatservice.dto.Member;
import com.algomeet.chatservice.dto.messageactions.ForwardRequest;
import com.algomeet.chatservice.dto.messageactions.ReactionEntry;
import com.algomeet.chatservice.dto.messageactions.ReactionsResponse;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageActionServiceTest {

    @Mock private MessageRepository repo;
    @Mock private MongoTemplate mongo;
    @Mock private MessageService messageService; // for unread counters
    @Mock private SimpMessagingSyncTemplate simp;
    @Mock private GroupClient groupClient;
    @Mock private MessageMapper mapper;
    @Mock private MediaService mediaService;

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
        
        when(repo.findById(any())).thenAnswer(inv -> {
            MessageDocument d = new MessageDocument();
            d.setId("replyId");
            return Optional.of(d);
        });
        
        ReplyRequest req = new ReplyRequest();
        req.setReplyToMessageId("orig");
        req.setReceiver("bob");
        req.setContent("reply");
        req.setMsgReplyTimeStamp(Instant.now().toEpochMilli());
        MessageDocument saved = service.replyTo(req, "alice", "null");

        assertThat(saved.getId()).isEqualTo("replyId");
        verify(simp).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any());
        verify(messageService).sendUnreadCountUpdate("bob");
    }

    @Test
    void applyReaction_groupNonMember_doesNotMutateMessage() {
        existing.setGroupId("51");
        when(groupClient.getGroupById("51")).thenReturn(group("51", "alice", "bob"));

        service.applyReaction("m1", "😀", true, "mallory");

        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(MessageDocument.class));
        verify(repo, never()).save(any(MessageDocument.class));
    }

    @Test
    void applyReaction_add_sameEmoji_togglesReactionOff() {
        MessageMetaData metaData = new MessageMetaData();
        metaData.setReactions(new LinkedHashMap<>(Map.of("👍", List.of("alice", "bob"))));
        existing.setMetaData(metaData);

        service.applyReaction("m1", "👍", true, "alice");

        assertThat(existing.getMetaData().getReactions()).containsEntry("👍", List.of("bob"));
        verify(repo).save(existing);
    }

    @Test
    void applyReaction_add_differentEmoji_replacesPreviousReaction() {
        MessageMetaData metaData = new MessageMetaData();
        Map<String, List<String>> reactions = new LinkedHashMap<>();
        reactions.put("👍", List.of("alice", "bob"));
        reactions.put("😀", List.of("charlie"));
        metaData.setReactions(reactions);
        existing.setMetaData(metaData);

        service.applyReaction("m1", "😀", true, "alice");

        assertThat(existing.getMetaData().getReactions())
                .containsEntry("👍", List.of("bob"))
                .containsEntry("😀", List.of("charlie", "alice"));
        verify(repo).save(existing);
    }

    @Test
    void applyReaction_remove_false_removesRequestedReaction() {
        MessageMetaData metaData = new MessageMetaData();
        metaData.setReactions(new LinkedHashMap<>(Map.of("👍", List.of("alice", "bob"))));
        existing.setMetaData(metaData);

        service.applyReaction("m1", "👍", false, "alice");

        assertThat(existing.getMetaData().getReactions()).containsEntry("👍", List.of("bob"));
        verify(repo).save(existing);
    }

    @Test
    void getGroupMessageReactions_returnsFlattenedEntriesWithUserKeys() {
        existing.setGroupMessage(true);
        existing.setGroupId("51");
        MessageMetaData metaData = new MessageMetaData();
        Map<String, List<String>> reactions = new LinkedHashMap<>();
        reactions.put("👍", List.of("alice", "bob"));
        reactions.put("😀", List.of("charlie"));
        metaData.setReactions(reactions);
        existing.setMetaData(metaData);

        when(groupClient.getGroupById("51")).thenReturn(group("51", "alice", "bob", "charlie"));

        ReactionsResponse response = service.getGroupMessageReactions("51", "m1", "alice");

        assertThat(response.getMessageId()).isEqualTo("m1");
        assertThat(response.getGroupId()).isEqualTo("51");
        assertThat(response.getTotalReactionsCount()).isEqualTo(3);
        assertThat(response.getReactions())
                .extracting(ReactionEntry::getUsername, ReactionEntry::getUserKey, ReactionEntry::getReaction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("charlie", "charlie-key", "😀"),
                        org.assertj.core.groups.Tuple.tuple("alice", "alice-key", "👍"),
                        org.assertj.core.groups.Tuple.tuple("bob", "bob-key", "👍")
                );
    }

    @Test
    void getGroupMessageReactions_nonMember_forbidden() {
        existing.setGroupMessage(true);
        existing.setGroupId("51");
        when(groupClient.getGroupById("51")).thenReturn(group("51", "alice", "bob"));

        assertThatThrownBy(() -> service.getGroupMessageReactions("51", "m1", "mallory"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void replyTo_groupReply_usesOriginalSenderAndDoesNotEchoSender() {
        MessageDocument original = new MessageDocument();
        original.setId("orig");
        original.setSender("bob");
        original.setGroupId("51");
        original.setContent("hello");
        when(repo.findById("orig")).thenReturn(Optional.of(original));
        when(groupClient.getGroupById("51")).thenReturn(group("51", "alice", "bob"));
        when(repo.save(any(MessageDocument.class))).thenAnswer(inv -> {
            MessageDocument d = inv.getArgument(0);
            d.setId("replyId");
            return d;
        });

        ReplyRequest req = new ReplyRequest();
        req.setReplyToMessageId("orig");
        req.setGroupId("51");
        req.setContent("reply");
        req.setMsgReplyTimeStamp(Instant.now().getEpochSecond());

        MessageDocument saved = service.replyTo(req, "alice", "alice-key");

        assertThat(saved.getReplyContent()).isNotNull();
        assertThat(saved.getReplyContent().getOriginalFrom()).isEqualTo("bob");
        assertThat(saved.getReplyContent().getOriginalMesg()).isEqualTo("hello");
        verify(simp).convertAndSendToUser(eq("alice"), eq("/queue/update_message"), any());
        verify(simp).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any());
        verify(simp, never()).convertAndSendToUser(eq("alice"), eq("/queue/messages"), any());
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
        fr.setMsgForwardTimeStamp(Instant.now().toEpochMilli());
        return fr;
    }

    private GroupDto group(String id, String... usernames) {
        GroupDto group = new GroupDto();
        group.setId(id);
        group.setMembers(List.of(usernames).stream().map(this::member).toList());
        return group;
    }

    private Member member(String username) {
        Member member = new Member();
        member.setUsername(username);
        member.setUserKey(username + "-key");
        return member;
    }
}
