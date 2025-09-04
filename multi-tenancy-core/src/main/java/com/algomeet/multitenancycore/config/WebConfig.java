package com.algomeet.multitenancycore.config;

import org.springframework.context.annotation.Bean;

import com.algomeet.multitenancycore.filter.TenantFilter;

import jakarta.servlet.http.HttpFilter;

public class WebConfig {
	/**
	 * Used to intercept all in-bound requests.
	 * @return
	 */
	@Bean
	public HttpFilter getHttpFilter() {
		return new TenantFilter();
	}

}
