package com.algomeet.chatservice.repository;

import com.algomeet.chatservice.document.MessageDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.mongodb.core.query.Meta;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@RequiredArgsConstructor
public class MessageTextSearchDao {

    private final MongoTemplate mongoTemplate;

    /**
     * Full-text search over messages visible to "viewer".
     * Optionally restrict to a conversation with "otherUser" (1:1).
     * <p>
     * NOTE: requires a text index on "content" (initializer).
     */
        public List<MessageDocument> searchVisibleByText(String viewer,
                String otherUser,   // nullable
                String queryText,
                Pageable pageable) {
            // 1) Visibility constraints
            Criteria notDeletedForAll = where("deletedForAll").ne(true);
            Criteria notDeletedForUser = new Criteria().orOperator(
                    where("deletedForUsers").exists(false),
                    where("deletedForUsers").ne(viewer) // exclude if viewer is in deletedForUsers
            );

            // 2) Optional: restrict to a direct chat (viewer <-> otherUser)
            Criteria participants = null;
            if (otherUser != null && !otherUser.isBlank()) {
                participants = new Criteria().orOperator(
                        new Criteria().andOperator(where("sender").is(viewer),    where("receiver").is(otherUser)),
                        new Criteria().andOperator(where("sender").is(otherUser), where("receiver").is(viewer))
                );
            }

            // 3) Text criteria for full-text search
            TextCriteria textCriteria = TextCriteria.forDefaultLanguage().matching(queryText);

            // 4) Base criteria (AND visibility [+ participants if present])
            Criteria base = new Criteria().andOperator(notDeletedForAll, notDeletedForUser);
            if (participants != null) {
                base = new Criteria().andOperator(base, participants);
            }

            // 5) Build TextQuery so $meta textScore sorting is handled by the driver
            TextQuery q = TextQuery.queryText(textCriteria).sortByScore();

            //    Add base filters
            q.addCriteria(base);

            //    Secondary order after score: time desc, then _id desc (deterministic)
            q.with(Sort.by(
                    Sort.Order.desc("timestamp"),
                    Sort.Order.desc("_id")
            ));

            //    Pagination
            q.with(pageable);

            //    Include text score in projection if supported (safe no-op otherwise)
            try {
                q.includeScore();
            } catch (Exception ignored) {
                // ignore if method not available in your Spring Data version
            }

            // 6) Execute
            return mongoTemplate.find(q, MessageDocument.class);
        }
}
