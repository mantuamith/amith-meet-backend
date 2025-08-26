package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.enums.LoginResponseType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginVerifyRequest {

    @NotBlank(message = "Login identifier is required")
    private String login;

    @NotBlank(message = "Password is required")
    private String password; // always required

    @NotNull(message = "Verification type is required")
    private VerificationType type;   // DIRECT, EMAIL, PHONE, TOTP

    // Required for EMAIL/PHONE/TOTP; optional for DIRECT
    private String code;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotNull(message = "deviceType is required and must be one of [WEB, ANDROID, IOS, DESKTOP]")
    private DeviceType deviceType;

    private String ipAddress; // optional

    // --- Conditional validation ---
    @AssertTrue(message = "Code is required for EMAIL/PHONE/TOTP verification")
    public boolean isCodePresentWhenOtpOrTotp() {
        if (type == null) return true;
        return switch (type.toResponseType()) {
            case EMAIL, PHONE, TOTP -> (code != null && !code.isBlank());
            case DIRECT -> true; // no code needed
        };
    }

    // --- Adapter enum for safe, case-insensitive JSON parsing ---
    public enum VerificationType {
        DIRECT, EMAIL, PHONE, TOTP;

        @JsonCreator
        public static VerificationType from(String raw) {
            if (raw == null) return null;
            return VerificationType.valueOf(raw.trim().toUpperCase());
        }

        @JsonValue
        public String toJson() { return name(); }

        public LoginResponseType toResponseType() {
            return LoginResponseType.valueOf(name());
        }
    }
}
