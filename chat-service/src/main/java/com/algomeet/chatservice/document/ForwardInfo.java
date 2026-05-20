package com.algomeet.chatservice.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
public class ForwardInfo {

    @Field("sequence")
    private Integer sequence;

    @Field("isForwarded")
    private boolean isForwarded = true;

    @Field("originalFrom")
    private String originalFrom; // user ID

    @Field("originalMessageId")
    private String originalMessageId;

    @Field("forwardedAt")
    private Long forwardedAt; // epoch millis
}
