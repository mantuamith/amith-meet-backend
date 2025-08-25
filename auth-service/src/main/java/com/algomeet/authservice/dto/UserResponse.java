package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String password;
    private String role;
    private boolean enabled;
    private Short loginTypePolicy;
    private String activeDeviceId;
}
