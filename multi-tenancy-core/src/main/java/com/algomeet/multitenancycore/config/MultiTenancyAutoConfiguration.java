package com.algomeet.multitenancycore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@Import({HibernateConfig.class, FeignConfig.class, WebConfig.class, AspectConfig.class})
public class MultiTenancyAutoConfiguration {	
}
