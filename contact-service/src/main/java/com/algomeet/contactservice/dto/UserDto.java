package com.algomeet.contactservice.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String password;
    private String role;
    private boolean enabled;
    private String activeDeviceId;
    private String deviceToken;
    private String clientPlatform;

    private String userKey;
    
    // NEW: expose loginTypePolicy so auth-service can enforce device policy
    private Short loginTypePolicy;
}
