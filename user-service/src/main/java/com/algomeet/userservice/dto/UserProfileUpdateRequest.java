package com.algomeet.userservice.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileUpdateRequest {
    private Short loginTypePolicy;
    private String country;
    private String region;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String registrationDeviceId;
    private String registrationDeviceType;
    private String passcode;
    private Boolean securityQuestionsEnabled;
    private String role;
    private Integer tenantId;
    private String lang;
}
