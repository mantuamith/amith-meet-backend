package com.algomeet.chatservice.dto.messageactions;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReactionCommand {
    @NotBlank
    private String messageId;

    @NotBlank
    private String emoji;   // e.g. "👍", "😂", "❤️"

    private boolean add = true; // true=add, false=remove
}
