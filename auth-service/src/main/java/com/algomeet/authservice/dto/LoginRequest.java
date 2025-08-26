package com.algomeet.authservice.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String login;
    private String password;

    // Optional: temporary back-compat if some FE still sends "email"
    private String email;
    public String getEffectiveLogin() {
        return (login != null && !login.isBlank()) ? login : email;
    }
}
