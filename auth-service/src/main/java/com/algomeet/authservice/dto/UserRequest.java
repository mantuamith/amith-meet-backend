package com.algomeet.authservice.dto;

import com.algomeet.authservice.util.SecurityUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest implements SecuredDto{
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
    
	@Override
	public void secured() {		
		// If user not admin
		if (!SecurityUtil.isAdminUser()) {
			setRole(null);
			setTenantId(null);	
		}	
	}
}
