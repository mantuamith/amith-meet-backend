package com.algomeet.multitenancy.hibernate.resolver;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.multitenancy.util.SchemaUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Object> {

    public static final String DEFAULT_TENANT = "public"; // fallback schema

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.getCurrentTenant();
        log.info("Tenant Id: {}", tenantId);
        
        String schema = SchemaUtil.getSchemaName(tenantId); 
        log.info("Using schema: {}", schema);
        
        return schema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}