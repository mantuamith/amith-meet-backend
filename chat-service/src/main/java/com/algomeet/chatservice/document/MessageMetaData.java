package com.algomeet.chatservice.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class MessageMetaData {

    @Field("reactionToMessageId")
    private String reactionToMessageId;

    @Field("reactions")
    private Map<String, List<String>> reactions; // emoji → list of userIds

    @Field("isEdited")
    private Boolean isEdited = false;

    @Field("isPinned")
    private Boolean isPinned = false;

    @Field("editedAt")
    private Long editedAt;
}
