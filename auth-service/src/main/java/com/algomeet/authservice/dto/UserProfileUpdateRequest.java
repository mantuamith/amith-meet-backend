package com.algomeet.authservice.dto;

import java.math.BigDecimal;

import org.springframework.security.access.AccessDeniedException;

import com.algomeet.authservice.enums.UserRole;
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
    /** Preferred language */
    private String lang;    
    
    private Integer messageRetentionDays;
    
	@Override
	public void secured() {
		// If user not admin
		if (SecurityUtil.isUserHasAdminRole()) {
			if (!SecurityUtil.isSAUser()) {
				setTenantId(null);
			}
		} else {
			setRole(null);
			setTenantId(null);
		}
		
		if (role != null 
				&& (UserRole.ROLE_SA.name().equals(role.trim().toUpperCase())
						|| "SA".equals(role.trim().toUpperCase()))){
			throw new AccessDeniedException("Not allowed to update user role to SA role");
		}
	}
}
