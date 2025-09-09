package com.algomeet.multitenancy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;


@Configuration
@Import({HibernateConfig.class, FeignConfig.class, WebConfig.class, AspectConfig.class, JwtConfig.class})
public class MultiTenancyAutoConfiguration {	
}
