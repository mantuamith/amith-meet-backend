package com.algomeet.multitenancycore.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "multi-tenancy")
public class MultiTenancyProperties {
	private List<String> packages;
}
