package com.algomeet.contactservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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
}
