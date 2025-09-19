package com.algomeet.controlservice.util;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import com.algomeet.controlservice.enums.UserRole;

@Slf4j
public class SecurityUtil {
	public static UserRole getUserRole() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		try {
			if (auth != null
					&& !CollectionUtils.isEmpty(auth.getAuthorities())) {
				return UserRole.valueOf(auth.getAuthorities()
						.iterator()
						.next()
						.getAuthority());
			}
		} catch(Exception ex) {
			log.error("Error retriving user role {}", ex.getMessage(), ex);
		}

		return null;
	}

	public static Integer getTenantId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		try {
			if (auth != null
					&& auth.getDetails() != null) {
				if(auth.getDetails() instanceof Map) {
					String tenantIdStr = (String) ((Map) auth.getDetails()).get("tenantId");
					if(StringUtils.hasLength(tenantIdStr)) {
						return Integer.valueOf(tenantIdStr);
					}
				}
			}
		} catch(Exception ex) {
			log.error("Error retriving user role {}", ex.getMessage(), ex);
		}
		// Use public schema id
		return 0;
	}

	public static boolean isAdminUser() {
		return UserRole.ROLE_ADMIN.equals(getUserRole());
	}

	public static boolean isSAUser() {
		return UserRole.ROLE_SA.equals(getUserRole());
	}
	
	public static boolean isUserHasAdminRole() {
		return ((SecurityUtil.isAdminUser() 
				|| SecurityUtil.isSAUser()));
	}
}
