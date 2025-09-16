package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    private String email;
    private String phone;
    private String username;
    private String password;          // already BCrypt

    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;

    private Boolean isEmailVerified;
    private Boolean isPhoneVerified;

    private String registrationIp;
    private String registrationDeviceId;
    private String registrationDeviceType;

    private Integer loginTypePolicy;  // from config if you enforce one
    
    private String role;
    private Integer tenantId;
}
