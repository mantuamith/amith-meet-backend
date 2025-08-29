package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordInitRequest {
    @NotBlank
    private String login;        // email or username/phone
    private String deviceId;     // optional (for auditing)
    private DeviceType deviceType;
}

