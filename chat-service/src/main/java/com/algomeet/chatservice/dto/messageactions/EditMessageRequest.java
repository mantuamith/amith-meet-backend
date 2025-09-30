package com.algomeet.chatservice.dto.messageactions;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditMessageRequest {
    @NotBlank
    private String messageId;

    @NotBlank
    private String newContent;
}
