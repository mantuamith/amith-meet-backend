package com.algomeet.chatservice.dto.messageactions;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyRequest {
    @NotBlank
    private String replyToMessageId;

    // target: either `receiver` (DM) or `groupId` (group)
    private String receiver; // userId
    private String groupId;  // group id

    @NotBlank
    private String content;

    private String clientMessageId;

    private Long msgReplyTimeStamp;
}
