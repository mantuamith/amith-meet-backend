package com.algomeet.multitenancy.config;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import com.algomeet.multitenancy.hibernate.SchemaMultiTenantConnectionProvider;
import com.algomeet.multitenancy.hibernate.resolver.TenantIdentifierResolver;
import com.algomeet.multitenancy.properties.MultiTenancyProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Import(MultiTenancyProperties.class)
public class HibernateConfig {
	@Autowired
	private JpaProperties jpaProperties;
	
	@Autowired
	private HibernateProperties hibernateProperties;
	
	@Autowired
	private MultiTenancyProperties multiTenancyProperties;
	
	@Bean
    public SchemaMultiTenantConnectionProvider schemaMultiTenantConnectionProvider() {
        return new SchemaMultiTenantConnectionProvider();
    }
	
	@Bean
	public TenantIdentifierResolver TenantIdentifierResolver() {
		return new TenantIdentifierResolver();
	}	
	
	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			EntityManagerFactoryBuilder builder,
			DataSource dataSource,
			SchemaMultiTenantConnectionProvider connectionProvider,
			TenantIdentifierResolver tenantResolver) {

		// Merge Spring Boot’s JPA + Hibernate defaults
		Map<String, Object> properties = new HashMap<>(
				hibernateProperties.determineHibernateProperties(
						jpaProperties.getProperties(),
						new HibernateSettings()
						)
				);
		log.info("hibernate properties {}", properties);
		//properties.put(AvailableSettings.MULTI_TENANT, MultiTenancyStrategy.SCHEMA.name());
		properties.put("hibernate.multiTenancy", "SCHEMA");
		properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
		properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
		
		String[] packages = Optional.ofNullable(multiTenancyProperties.getPackages())
				.orElse(List.of("com.algomeet"))
				.stream()
				.toArray(String[]::new);

		return builder
				.dataSource(dataSource)
				.packages(packages) // your JPA entities
				.properties(properties)
				.build();
	}
}
