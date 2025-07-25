package com.algomeet.contactservice.dto;

import lombok.Data;

@Data
public class UserResponse {
    private String userId;
    private String username;
    private String email;
    private String phone;
}