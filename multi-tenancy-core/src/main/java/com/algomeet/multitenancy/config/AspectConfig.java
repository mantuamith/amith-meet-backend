package com.algomeet.multitenancy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.algomeet.multitenancy.aspects.RepositoryTransactionInterceptor;
import com.algomeet.multitenancy.aspects.UsePublicSchemaAspect;

@Configuration
public class AspectConfig {
	
	@Bean
	public UsePublicSchemaAspect tenantAwareSwitchOffAspect() {
		return new UsePublicSchemaAspect();
	}
	
	@Bean
	public RepositoryTransactionInterceptor repositoryTransactionInterceptor() {
		return new RepositoryTransactionInterceptor();
	}
}
