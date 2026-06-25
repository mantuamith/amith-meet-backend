package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_settings")
public class ConversationSetting {

    /**
     * MongoDB document field names.
     */
    public static final String FIELD_ID = "_id";
    public static final String FIELD_MESSAGE_RETENTION_DAYS = "messageRetentionDays";

    /**
     * Unique conversation identifier composed of the two participant user keys.
     *
     * <p>Format:</p>
     * <pre>
     * lowerUserKey_higherUserKey
     * </pre>
     *
     * <p>The user keys are always ordered lexicographically from lowest to
     * highest to ensure a single, deterministic document exists for a
     * direct conversation regardless of which participant initiates it.</p>
     */
    @Id
    private String id;

    /**
     * Number of days messages are retained before becoming eligible for
     * automatic deletion.
     *
     * <p>A value of {@code -1} indicates that messages never expire.</p>
     */
    private Integer messageRetentionDays;
}
