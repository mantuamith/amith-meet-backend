package com.algomeet.multitenancy.util;

import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.SchemaConstants;
import com.algomeet.multitenancy.hibernate.resolver.TenantIdentifierResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SchemaUtil {
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
		return (StringUtils.hasLength(tenantId)) ? (SchemaConstants.prefix + formatTenantId(tenantId)) 
				: TenantIdentifierResolver.DEFAULT_TENANT;
	}
}
