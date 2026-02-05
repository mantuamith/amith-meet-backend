package com.algomeet.chatservice.util;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecurityUtil {

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
	
	public static String getUserKey() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		try {
			if (auth != null
					&& auth.getDetails() != null) {
				if(auth.getDetails() instanceof Map) {
					return (String) ((Map) auth.getDetails()).get("user_key");					
				}
			}
		} catch(Exception ex) {
			log.error("Error retriving user role {}", ex.getMessage(), ex);
		}

		return null;
	}
}
