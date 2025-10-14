package com.algomeet.authservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import com.algomeet.authservice.util.SecurityUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse implements SecuredDto{
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
    private String role;
    private Integer tenantId;
    private String lang;
    
    public Boolean getSecurityQuestionsEnabled() {
    	if(securityQuestionsEnabled == null) {
    		return Boolean.FALSE;
    	}
    	
    	return securityQuestionsEnabled;
    }  
    
    @Override
	public void secured() {
    	// Check if user has authority, access right or data owner
		if (!SecurityUtil.isSAUser()) {			
			if (SecurityUtil.isUserHasAdminRole()) { 
				// User has admin role but lower than "SA" check if user has same tenant Id to the record his/she is trying to access.
			    if (SecurityUtil.getTenantId() != tenantId) {
			    	throw new AccessDeniedException("Access denied");
			    }
			    
			    // hide passcode
			    passcode = null;
			} else {
				// For ordinary users, check the user key to identify if the user is the data owner.							
				if (!id.equals(SecurityUtil.getUserKey())) {
					throw new AccessDeniedException("Access denied");
				}				
			}					
		}	
				
		// Hide fields for non-admin users
		if (!SecurityUtil.isUserHasAdminRole()) {
			tenantId = null;
		}
	}
}