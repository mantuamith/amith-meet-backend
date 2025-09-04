package com.algomeet.multitenancycore.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.algomeet.multitenancycore.constants.HttpHeaderConstants;
import com.algomeet.multitenancycore.context.TenantContext;

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