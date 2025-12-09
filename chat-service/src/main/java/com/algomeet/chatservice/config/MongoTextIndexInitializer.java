package com.algomeet.chatservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.document.MessageDocument;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.*;
import java.util.stream.Collectors;

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

        // Define the desired text index
        Map<String, String> desiredKeys = Map.of(
                "content", "text"
                // ,"text", "text"
        );
        Map<String, Integer> desiredWeights = Map.of(
                // "content", 10
        );
        String defaultLanguage = "english";

        MongoDatabase db = mongoTemplate.getDb();
        String collName = mongoTemplate.getCollectionName(MessageDocument.class);
        MongoCollection<Document> coll = db.getCollection(collName);

        // Read raw index specs
        List<Document> indexes = coll.listIndexes(Document.class).into(new ArrayList<>());

        // Find existing text index (there can be only one)
        Document existingTextIdx = indexes.stream()
                .filter(this::isTextIndex)
                .findFirst()
                .orElse(null);

        if (existingTextIdx == null) {
            log.info("[IDX] No text index found on '{}'. Creating '{}'.", collName, INDEX_NAME);
            createTextIndex(db, collName, desiredKeys, desiredWeights, defaultLanguage, INDEX_NAME);
            return;
        }

        Document existingKeysDoc = (Document) existingTextIdx.get("key"); // e.g. {content:"text"}
        Map<String, String> existingKeys = docToStringMap(existingKeysDoc);
        Map<String, Integer> existingWeights = docToIntMap((Document) existingTextIdx.get("weights"));

        boolean keysMatch = normalizeMap(existingKeys).equals(normalizeMap(desiredKeys));
        boolean weightsMatch = normalizeMap(existingWeights).equals(normalizeMap(desiredWeights));

        String existingName = existingTextIdx.getString("name");

        if (keysMatch && weightsMatch) {
            if (!INDEX_NAME.equals(existingName)) {
                log.info("[IDX] Compatible text index already present as '{}'; keeping it.", existingName);
            } else {
                log.info("[IDX] Text index '{}' already present with matching definition.", INDEX_NAME);
            }
            return;
        }

        log.warn("[IDX] Existing text index '{}' differs from desired '{}'. Dropping and recreating.",
                existingName, INDEX_NAME);
        coll.dropIndex(existingName);
        createTextIndex(db, collName, desiredKeys, desiredWeights, defaultLanguage, INDEX_NAME);
        log.info("[IDX] Recreated {}", INDEX_NAME);
    }

    private boolean isTextIndex(Document indexDoc) {
        Document key = (Document) indexDoc.get("key");
        if (key == null) return false;
        for (Object v : key.values()) {
            if (v != null && "text".equalsIgnoreCase(String.valueOf(v))) {
                return true;
            }
        }
        return false;
    }

    private void createTextIndex(MongoDatabase db,
                                 String collName,
                                 Map<String, String> keys,
                                 Map<String, Integer> weights,
                                 String defaultLanguage,
                                 String name) {

        Document index = new Document();
        index.put("key", new Document(keys));
        index.put("name", name);
        if (!weights.isEmpty()) index.put("weights", new Document(weights));
        if (defaultLanguage != null) index.put("default_language", defaultLanguage);

        Document cmd = new Document("createIndexes", collName)
                .append("indexes", List.of(index));

        // Use the MongoDatabase we already have
        db.runCommand(cmd);
    }

    private Map<String, String> docToStringMap(Document d) {
        if (d == null) return Collections.emptyMap();
        return d.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() == null ? null : String.valueOf(e.getValue())
                ));
    }

    private Map<String, Integer> docToIntMap(Document d) {
        if (d == null) return Collections.emptyMap();
        Map<String, Integer> m = new HashMap<>();
        for (Map.Entry<String, Object> e : d.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number n) {
                m.put(e.getKey(), n.intValue());
            } else if (v != null) {
                try { m.put(e.getKey(), Integer.parseInt(String.valueOf(v))); }
                catch (NumberFormatException ignore) {}
            }
        }
        return m;
    }

    private <K, V> Map<K, V> normalizeMap(Map<K, V> in) {
        if (in == null) return Collections.emptyMap();
        return new TreeMap<>(in); // sort by key for stable equals()
    }
}
