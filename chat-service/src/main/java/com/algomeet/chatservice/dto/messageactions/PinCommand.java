package com.algomeet.chatservice.dto.messageactions;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PinCommand {
    @NotBlank
    private String messageId;

    private boolean pin; // true=pin, false=unpin
}
