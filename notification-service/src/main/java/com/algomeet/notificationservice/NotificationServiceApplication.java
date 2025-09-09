package com.algomeet.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = {
	    "com.algomeet.**.feign"})
@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
		// This code is temporary fix - need to be resolved
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.net.preferIPv6Addresses", "false");
		
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}