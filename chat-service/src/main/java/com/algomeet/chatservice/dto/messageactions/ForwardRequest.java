package com.algomeet.chatservice.dto.messageactions;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForwardRequest {
    @NotBlank
    private String originalMessageId;

    // forward to either user or group
    private String receiver; // userId
    private String groupId;  // group id
}
