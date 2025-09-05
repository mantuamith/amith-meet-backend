package com.algomeet.multitenancycore.util;

import org.springframework.util.StringUtils;

import com.algomeet.multitenancycore.constants.SchemaConstants;
import com.algomeet.multitenancycore.hibernate.resolver.TenantIdentifierResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchemaUtil {
	public static String formatTenantId(String strId) {
		if (StringUtils.hasText(strId)) {
			try {
				int id = Integer.parseInt(strId.trim());
				return String.format("%04d", id);
			} catch (Exception ex) {
				log.error("Error parsing tenant id: {}", strId, ex);
				throw ex;
			}			
		}
		
		return null;
	}
	
	public static String getSchemaName(String tenantId) {
		return (tenantId != null) ? (SchemaConstants.prefix + formatTenantId(tenantId)) 
				: TenantIdentifierResolver.DEFAULT_TENANT;
	}
}
