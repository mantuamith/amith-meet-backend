package com.algomeet.multitenancy.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.util.JwtHelper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class JwtConfig implements InitializingBean{
	@Value("${jwt.secret:#{null}}")
	private String secret;

	
	@Override
	public void afterPropertiesSet() throws Exception {
		if (!StringUtils.hasText(secret)) {
			log.info("Not found Jwt secret configuration property.");
		}
	}
	
	@ConditionalOnProperty(name = "jwt.secret")
	@Bean
	public JwtHelper jwtHelper() {
		return new JwtHelper(secret);
	}
}
