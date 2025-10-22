package com.algomeet.chatservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.document.MessageDocument;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class MongoTextIndexInitializer {

    private final MongoTemplate mongoTemplate;

    @Value("${chat.index.auto-create:true}")
    private boolean autoCreate;

    private static final String INDEX_NAME = "idx_text_content_v1";

    @PostConstruct
    public void ensure() {
        if (!autoCreate) {
            log.info("[IDX] Auto-create disabled via chat.index.auto-create=false");
            return;
        }
        IndexOperations ops = mongoTemplate.indexOps(MessageDocument.class);
        List<IndexInfo> infos = ops.getIndexInfo();
        boolean exists = infos.stream().anyMatch(ii -> INDEX_NAME.equals(ii.getName()));
        if (exists) {
            log.info("[IDX] {} already exists", INDEX_NAME);
            return;
        }

        TextIndexDefinition def = new TextIndexDefinition.TextIndexDefinitionBuilder()
                .onField("content")
                .withDefaultLanguage("english")
                .named(INDEX_NAME)
                .build();

        ops.ensureIndex(def);
        log.info("[IDX] Created {}", INDEX_NAME);
    }
}
