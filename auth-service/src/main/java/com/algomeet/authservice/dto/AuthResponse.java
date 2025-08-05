package com.algomeet.authservice.dto;


import com.algomeet.authservice.enums.ResponseCode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String code;
    private String message;
    private UserResponse user;
    private String accessToken;
    private String refreshToken;

    public AuthResponse(String code, String defaultMessage, UserResponse user) {
        this.code = code;
        this.message = defaultMessage;
        this.user = user;
        this.accessToken = null;
        this.refreshToken = null;
    }


    public static AuthResponse from(ResponseCode responseCode, UserResponse user) {
        return new AuthResponse(responseCode.getCode(), responseCode.getDefaultMessage(), user);
    }

    public static AuthResponse from(ResponseCode responseCode, UserResponse user, String accessToken, String refreshToken) {
        return new AuthResponse(
                responseCode.getCode(),
                responseCode.getDefaultMessage(),
                user,
                accessToken,
                refreshToken
        );
    }
}
