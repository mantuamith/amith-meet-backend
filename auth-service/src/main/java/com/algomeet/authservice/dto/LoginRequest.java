package com.algomeet.authservice.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {
    private String login;
    private String password;

    // Optional: temporary back-compat if some FE still sends "email"
    private String email;
    
    /**
     * Coming from Apple APN, or Google Firebase
     */
    private String deviceToken;
    /**
     * Value can be (ANDROID, IOS, WEB. HARMONYOS)
     */
    @Pattern(
            regexp = "(?i)^(ANDROID|IOS|WEB|HARMONYOS)$",
            message = "Platform must be one of: ANDROID, IOS, WEB, HARMONYOS"
        )
    private String clientPlatform;
    
    public String getEffectiveLogin() {
        return (login != null && !login.isBlank()) ? login : email;
    }
    

    
}
