package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordResetRequest {
    @NotBlank
    private String ticketId;      // returned by /password/forgot/verify
    @NotBlank
    private String newPassword;   // will be BCrypted by auth-service
}
