package com.algomeet.common.document;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSettingDocument {

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
    protected String id;

    /**
     * Number of days messages are retained before becoming eligible for
     * automatic deletion.
     *
     * <p>A value of {@code -1} indicates that messages never expire.</p>
     */
    protected Integer messageRetentionDays;
}
