package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordVerifyRequest {
    @NotBlank
    private String login;        // email or phone (or username -> resolves to email)
    @NotBlank
    private String code;         // OTP from email/SMS
    @NotBlank
    private String newPassword;  // raw; will be BCrypted in auth-service
    private String deviceId;
    private DeviceType deviceType;
}
