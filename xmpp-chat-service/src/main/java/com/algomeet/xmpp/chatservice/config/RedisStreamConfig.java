package com.algomeet.xmpp.chatservice.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamReceiver;

@Configuration
public class RedisStreamConfig {

	@Bean
	public StreamReceiver<String, MapRecord<String, String, String>> streamReceiver(
	        ReactiveRedisConnectionFactory connectionFactory) {

	    StreamReceiver.StreamReceiverOptions<String, MapRecord<String, String, String>> options =
	            StreamReceiver.StreamReceiverOptions.builder()
	                    .pollTimeout(Duration.ofSeconds(2))
	                    .build();

	    return StreamReceiver.create(connectionFactory, options);
	}
}