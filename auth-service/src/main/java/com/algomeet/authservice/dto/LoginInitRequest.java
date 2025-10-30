package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginInitRequest {
    @NotBlank(message = "Login identifier (email/username/phone) is required")
    private String login;        // email / phone / username

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Device ID is required")
    private String deviceId;     // client-generated

    @NotNull(message = "deviceType is required and must be one of [WEB, ANDROID, IOS, DESKTOP]")
    private DeviceType deviceType;   // WEB | ANDROID | IOS | DESKTOP

    private String ipAddress;    // optional (server will still detect)

    private Boolean overrideExisting;
    
    private String deviceToken;
    
    /** User preferred language */
    private String lang;
}
