// request
package com.algomeet.chatservice.dto.clearchat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClearChatRequest {
    @NotBlank
    private String contactId; // the other participant
}
