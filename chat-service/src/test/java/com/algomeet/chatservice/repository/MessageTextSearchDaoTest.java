package com.algomeet.chatservice.repository;

import com.algomeet.chatservice.document.MessageDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MessageTextSearchDao.class)
class MessageTextSearchDaoTest {

    @Autowired
    MongoTemplate mongo;

    @Autowired
    MessageTextSearchDao dao;

    @BeforeEach
    void setup() {
        mongo.dropCollection(MessageDocument.class);

        // Ensure text index on "content"
        TextIndexDefinition textIndex = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("content")
                .build();
        mongo.indexOps(MessageDocument.class).ensureIndex(textIndex);

        // Base docs:
        // viewer = "viewer", other = "alice", third = "charlie"
        mongo.save(doc("s1", "viewer", "alice", "hello alice from viewer", false, null, null, Instant.now().minusSeconds(60)));
        mongo.save(doc("s2", "alice", "viewer", "reply to viewer about hello", false, null, null, Instant.now().minusSeconds(30)));
        mongo.save(doc("s3", "charlie", "viewer", "unrelated content greetings", false, null, null, Instant.now().minusSeconds(10)));

        // Deleted for all => invisible
        mongo.save(doc("s4", "viewer", "alice", "this is deleted for all hello", true, null, null, Instant.now().minusSeconds(5)));

        // Soft-deleted for viewer => invisible to viewer
        mongo.save(doc("s5", "viewer", "alice", "hidden only for viewer hello", false, Set.of("viewer"), null, Instant.now().minusSeconds(5)));
    }

    private static MessageDocument doc(String id,
                                       String sender,
                                       String receiver,
                                       String content,
                                       boolean deletedForAll,
                                       Set<String> deletedForUsers,
                                       String groupId,
                                       Instant ts) {
        MessageDocument d = new MessageDocument();
        d.setId(id);
        d.setSender(sender);
        d.setReceiver(receiver);
        d.setContent(content);
        d.setDeletedForAll(deletedForAll);
        if (deletedForUsers != null) d.setDeletedForUsers(deletedForUsers);
        d.setTimestamp(ts);
        d.setGroupMessage(false);
        d.setGroupId(groupId);
        return d;
    }

    @Test
    @DisplayName("Full-text search across all visible messages for viewer")
    void search_allVisible() {
        var out = dao.searchVisibleByText("viewer", null, "hello", PageRequest.of(0, 10));
        // s1, s2 match 'hello'; s4 is deletedForAll (exclude); s5 is deleted for viewer (exclude)
        assertThat(out).extracting(MessageDocument::getId).containsExactlyInAnyOrder("s1", "s2");
    }

    @Test
    @DisplayName("Restrict to conversation viewer <-> alice")
    void search_onlyConversation() {
        var out = dao.searchVisibleByText("viewer", "alice", "hello", PageRequest.of(0, 10));
        assertThat(out).extracting(MessageDocument::getId).containsExactlyInAnyOrder("s1", "s2");
        // s3 (charlie->viewer) should be excluded by participants filter
    }

    @Test
    @DisplayName("Soft delete for viewer removes only those docs for that viewer")
    void search_deletedForUsers() {
        var out = dao.searchVisibleByText("viewer", "alice", "hidden", PageRequest.of(0, 10));
        assertThat(out).isEmpty(); // s5 hidden for viewer

        var outForAlice = dao.searchVisibleByText("alice", "viewer", "hidden", PageRequest.of(0, 10));
        assertThat(outForAlice).extracting(MessageDocument::getId).containsExactly("s5"); // visible to alice
    }

    @Test
    @DisplayName("Pagination & sort: deterministic order (score desc by driver, then timestamp desc, _id desc)")
    void pagination_sorting() {
        // query 'hello' returns s1 and s2; s2 newer than s1
        var page0 = dao.searchVisibleByText("viewer", null, "hello", PageRequest.of(0, 1));
        var page1 = dao.searchVisibleByText("viewer", null, "hello", PageRequest.of(1, 1));

        assertThat(page0).hasSize(1);
        assertThat(page1).hasSize(1);
        // depending on score ties, secondary sort is timestamp desc
        assertThat(page0.get(0).getId()).isIn("s2", "s1");
        assertThat(page1.get(0).getId()).isIn("s2", "s1");
        assertThat(page0.get(0).getId()).isNotEqualTo(page1.get(0).getId());
    }
}
