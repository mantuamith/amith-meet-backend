package com.algomeet.signalservice.demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {"com", "org.signal"})
public class AppConfig {
    // You can define @Bean methods here if needed
}