package com.algomeet.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    private String email;
    private String phone;
    private String username;
    private String password;              // already BCrypted

    private String country;
    private String region;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private Boolean isEmailVerified;      // from auth-service channel
    private Boolean isPhoneVerified;

    private String registrationIp;
    private String registrationDeviceId;
    private String registrationDeviceType;

    private Integer loginTypePolicy;
    
    /**
     * Coming from Apple APN, or Google Firebase
     */
    private String deviceToken;

    private String role;
    private Integer tenantId;
    
    /** User preferred language */
    private String lang;
}
