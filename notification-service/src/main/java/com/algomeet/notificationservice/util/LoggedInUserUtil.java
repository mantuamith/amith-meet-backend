package com.algomeet.notificationservice.util;

import org.springframework.security.core.context.SecurityContextHolder;

public class LoggedInUserUtil {
	public static String getUsername() {
		return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
	}
}
