package com.algomeet.userservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@AllArgsConstructor
public class UserProfileResponse {
    private UUID id;
    private Short loginTypePolicy;
    private String country;
    private String region;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String registrationDeviceId;
    private String registrationDeviceType;
    private Instant registrationDate;
    private String passcode;
    private Boolean securityQuestionsEnabled;
    private String username;
    private String email;
    private String phone;
    
}