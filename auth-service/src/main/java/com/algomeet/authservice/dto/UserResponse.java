package com.algomeet.authservice.dto;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import com.algomeet.authservice.enums.UserRole;
import com.algomeet.authservice.util.SecurityUtil;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse implements SecuredDto{
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String password;
    private String role;
    private boolean enabled;
    private Short loginTypePolicy;
    private String activeDeviceId;
    private UUID userKey;    
    /**
     * Coming from Apple APN, or Google Firebase
     */
    private String deviceToken;
    /**
     * Value can be (ANDROID, IOS, WEB. HARMONYOS)
     */
    private String deviceType;
    private Integer tenantId;

    @SuppressWarnings("unchecked")
    public UserResponse(Map<String, Object> map) {
        if (map == null) return;

        this.id = map.get("id") != null ? ((Number) map.get("id")).longValue() : null;
        this.username = (String) map.get("username");
        this.email = (String) map.get("email");
        //this.password = (String) map.get("password");
        this.activeDeviceId = (String) map.get("activeDeviceId");

        Object ltp = map.get("loginTypePolicy");
        if (ltp instanceof Number) {
            this.loginTypePolicy = ((Number) ltp).shortValue();
        } else if (ltp instanceof String) {
            try {
                this.loginTypePolicy = Short.valueOf((String) ltp);
            } catch (NumberFormatException ignored) {}
        }

        // role & enabled are optional in response
        this.role = (String) map.get("role");
        this.enabled = map.get("enabled") != null && Boolean.TRUE.equals(map.get("enabled"));
        this.tenantId = (Integer) map.get("tenantId");
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
			} else {
				// For ordinary users, check the user key to identify if the user is the data owner.
				if (userKey == null) {
					throw new RuntimeException("Invalid record userKey has null value");
				}
								
				if (!(userKey.equals(SecurityUtil.getUserKey()) 	
						/* SecurityUtil.getUserKey() returned null if user token has not passed in the request header yet.
						 * This will happen during registration or login those login URIs are exempted from authentication.
						 */	
						|| (SecurityUtil.getUserKey() == null))) {
					throw new AccessDeniedException("Access denied");
				}				
			}					
		}	
				
		// Hide fields for non-admin users
		if (SecurityUtil.isUserHasAdminRole()) {
			tenantId = null;
		}
	}
}
