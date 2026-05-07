package com.algomeet.chatservice.dto.messageactions;

import com.algomeet.chatservice.document.EncrytionMetadata;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ForwardRequest {
    private Integer sequence;
    @NotBlank
    private String originalMessageId;

    // forward to either user or group
    private String receiver; // userId
    private String groupId;  // group id

    private String clientMessageId;

    private Long msgForwardTimeStamp;

    private List<EncrytionMetadata> encryptionMetadata;

    private String fromKey;  // UUID string
    private String toKey;    // UUID string

    private String content;
}
