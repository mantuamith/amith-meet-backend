package com.algomeet.chatservice.dto.messageactions;

import com.algomeet.chatservice.document.EncrytionMetadata;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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

    private List<EncrytionMetadata> encryptionMetadata;

    private String fromKey;  // UUID string
    private String toKey;    // UUID string
}
