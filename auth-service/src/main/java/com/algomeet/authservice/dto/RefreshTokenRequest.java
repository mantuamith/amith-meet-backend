package com.algomeet.authservice.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken; //its access token
}
