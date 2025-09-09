package com.algomeet.multitenancy.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.algomeet.multitenancy.constants.HttpHeaderConstants;
import com.algomeet.multitenancy.context.TenantContext;

@Configuration
public class FeignConfig {	
	/**
	 * Used for feign out-bound call to initialize the header
	 * 
	 * @return
	 */
    @Bean
    public RequestInterceptor tenantRequestInterceptor() {
        return (RequestTemplate template) -> {
            // Add tenant header
            template.header(HttpHeaderConstants.TENANT_ID, TenantContext.getCurrentTenant());
        };
    }
}