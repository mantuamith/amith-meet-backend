package com.algomeet.multitenancy.filter;

import java.io.IOException;

import com.algomeet.multitenancy.constants.HttpHeaderConstants;
import com.algomeet.multitenancy.context.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Used to intercept all incoming http requests.
 */
@Slf4j
public class TenantFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

	@Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String tenantId = request.getHeader(HttpHeaderConstants.TENANT_ID);        
        log.info("TenantFilter: {} ", tenantId);
        
        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
        	TenantContext.clear(); // cleanup
        }
    }
}
