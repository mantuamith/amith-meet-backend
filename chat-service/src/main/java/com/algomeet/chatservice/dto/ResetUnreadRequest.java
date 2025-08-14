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
