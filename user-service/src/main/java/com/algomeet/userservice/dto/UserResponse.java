package com.algomeet.userservice.dto;

import com.algomeet.userservice.model.User;
import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;
    private boolean enabled;
    private String activeDeviceId;
    /**
     * Coming from Apple APN, or Google Firebase
     */
    private String deviceToken;
    /**
     * Value can be (ANDROID, IOS, WEB. HARMONYOS)
     */
    private String clientPlatform;

    // NEW: expose loginTypePolicy so auth-service can enforce device policy
    private Short loginTypePolicy;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.loginTypePolicy = user.getLoginTypePolicy(); // <-- added
        this.activeDeviceId = user.getActiveDeviceId();
    }
}
