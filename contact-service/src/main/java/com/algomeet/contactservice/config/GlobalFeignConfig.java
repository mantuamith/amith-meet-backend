package com.algomeet.contactservice.config;

import java.util.Collections;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.DeserializationFeature;

import feign.codec.Decoder;

@Configuration
public class GlobalFeignConfig {

	/**
	 * Used to ignore feign DTO response unknown fields
	 * @return
	 */
    @Bean
    public Decoder feignDecoder() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.getObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ResponseEntityDecoder(new SpringDecoder(() -> new HttpMessageConverters(Collections.singletonList(converter))));
    }
}