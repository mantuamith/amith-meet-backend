package com.algomeet.multitenancycore.hibernate.resolver;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import com.algomeet.multitenancycore.constants.SchemaConstants;
import com.algomeet.multitenancycore.context.TenantContext;
import com.algomeet.multitenancycore.util.TenantIdUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Object> {

    public static final String DEFAULT_TENANT = "public"; // fallback schema

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantIdUtil.formatTenantId(TenantContext.getCurrentTenant());
        log.info("Tenant Id: {}", tenantId);
        
        String schema = (tenantId != null) ? (SchemaConstants.prefix + tenantId): DEFAULT_TENANT;
        log.info("Using schema: {}", schema);
        
        return schema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}