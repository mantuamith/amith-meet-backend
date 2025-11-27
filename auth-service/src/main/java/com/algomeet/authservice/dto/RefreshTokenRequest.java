package com.algomeet.authservice.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;  //refresh token to identify right user to revoke.
}
