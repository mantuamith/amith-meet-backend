package com.algomeet.multitenancycore.util;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantIdUtil {
	public static String formatTenantId(String strId) {
		if (StringUtils.hasText(strId) 
				&& isNumeric(strId.trim())) {
			try {
				int id = Integer.parseInt(strId.trim());
				return String.format("%04d", id);
			} catch (Exception ex) {
				log.error("Error parsing tenant id: {}", strId, ex);
			}			
		}
		
		return null;
	}
	
	private static boolean isNumeric(String str) {
	    return str != null && str.matches("\\d+");
	}
}
