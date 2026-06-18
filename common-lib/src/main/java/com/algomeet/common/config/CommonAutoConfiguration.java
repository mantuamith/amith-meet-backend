package com.algomeet.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.algomeet.common.properties.CommonRedisStreamProperties;
import com.algomeet.common.properties.CommonRedisTopicProperties;


@Configuration
@Import({CommonRedisStreamProperties.class, CommonRedisTopicProperties.class})
public class CommonAutoConfiguration {	
}
