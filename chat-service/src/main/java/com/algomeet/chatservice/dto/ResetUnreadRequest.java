package com.algomeet.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetUnreadRequest {
    @NotBlank
    private String sender;

    @NotBlank
    private String receiver;
}

//TODO(migration):
// later, add contactKey or senderKey/receiverKey in addition to existing fields,
// and make the server accept either.
