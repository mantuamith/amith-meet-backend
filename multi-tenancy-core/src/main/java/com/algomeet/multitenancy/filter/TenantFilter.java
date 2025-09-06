package com.algomeet.multitenancy.filter;

import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.constants.HttpHeaderConstants;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.multitenancy.util.JwtHelper;

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
    
    @Autowired(required = false)
    private JwtHelper jwtHelper;

	@Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {		
		String tenantId = null;
		
		// First get tenant ID from authorization token 
		if(jwtHelper != null) {
			tenantId = jwtHelper.getTenantId(request);
		}
		
		if (!StringUtils.hasLength(tenantId)) {
			// If tenant ID not found from authorization token, get it from request header
			tenantId = request.getHeader(HttpHeaderConstants.TENANT_ID);   
		}
   
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
