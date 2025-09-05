package com.algomeet.multitenancy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.algomeet.multitenancy.aspects.RepositoryTransactionInterceptor;
import com.algomeet.multitenancy.aspects.TenantAwareSwitchOffAspect;

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
