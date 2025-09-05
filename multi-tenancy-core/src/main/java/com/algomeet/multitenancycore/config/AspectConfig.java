package com.algomeet.multitenancycore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.algomeet.multitenancycore.aspects.RepositoryTransactionInterceptor;
import com.algomeet.multitenancycore.aspects.TenantAwareSwitchOffAspect;

@Configuration
public class AspectConfig {
	
	@Bean
	public TenantAwareSwitchOffAspect tenantAwareSwitchOffAspect() {
		return new TenantAwareSwitchOffAspect();
	}
	
	@Bean
	public RepositoryTransactionInterceptor repositoryTransactionInterceptor() {
		return new RepositoryTransactionInterceptor();
	}
}
