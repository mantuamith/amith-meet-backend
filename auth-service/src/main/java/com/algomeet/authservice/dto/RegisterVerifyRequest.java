package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// RegisterVerifyRequest.java
@Data
public class RegisterVerifyRequest {
    @NotBlank
    private String transactionId;       // returned from /init
    @NotBlank
    private String type;                // EMAIL | PHONE
    @NotBlank
    private String code;                // OTP/TOTP
    @NotBlank
    private String deviceId;
    @NotNull
    private DeviceType deviceType;
    private String ipAddress;
}
