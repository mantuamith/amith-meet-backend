package com.algomeet.multitenancy.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "multi-tenancy")
public class MultiTenancyProperties {
	/**
	 * Entity classes package locations.
	 */
	private List<String> packages;
}
