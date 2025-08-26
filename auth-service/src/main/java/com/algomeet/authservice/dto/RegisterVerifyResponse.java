package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterVerifyResponse {
    private String type;
    private String message;
    private UserResponse user; // optional
}
