package com.algomeet.authservice.dto;

import java.math.BigDecimal;

import com.algomeet.authservice.util.SecurityUtil;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileUpdateRequest implements SecuredDto{
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
    
	@Override
	public void secured() {
		// If user not admin
		if (!SecurityUtil.isAdminUser()) {
			setRole(null);
			setTenantId(null);
		}		
	}
}
