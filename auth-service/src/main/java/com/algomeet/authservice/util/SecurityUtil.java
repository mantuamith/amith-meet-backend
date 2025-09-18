package com.algomeet.authservice.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;

import com.algomeet.authservice.enums.UserRole;

import lombok.extern.slf4j.Slf4j;

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
	
	public static boolean isAdminUser() {
		return UserRole.ADMIN.equals(getUserRole());
	}
}
