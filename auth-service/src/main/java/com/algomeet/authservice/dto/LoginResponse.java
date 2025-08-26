package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.LoginResponseType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class LoginResponse {
    private LoginResponseType type;
    private String message;
    private String accessToken;
    private String refreshToken;
    private UserResponse user;

    public LoginResponse(LoginResponseType type, String message, String accessToken, String refreshToken) {
        this.type = type;
        this.message = message;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static LoginResponse direct(String message, String accessToken, String refreshToken) {
        return new LoginResponse(LoginResponseType.DIRECT, message, accessToken, refreshToken);
    }


    public LoginResponse(LoginResponseType type, String message) {
        this.type = type;
        this.message = message;
    }

    public static LoginResponse direct(String msg) {
        return new LoginResponse(LoginResponseType.DIRECT, "Direct login allowed");
    }
    public static LoginResponse emailOtp(String msg) {
        return new LoginResponse(LoginResponseType.EMAIL, msg);
    }
    public static LoginResponse phoneOtp(String msg) {
        return new LoginResponse(LoginResponseType.PHONE, msg);
    }
    public static LoginResponse totp(String msg) {
        return new LoginResponse(LoginResponseType.TOTP, msg);
    }
}
