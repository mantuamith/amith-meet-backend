package com.algomeet.chatservice.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
public class ReplyContent {
    @Field("originalMesg")
    private String originalMesg; // user ID

    @Field("originalMessageId")
    private String originalMessageId;

    @Field("originalFrom")
    private String originalFrom; // epoch millis
}
