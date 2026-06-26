package com.algomeet.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class NotificationServiceApplication {

	public static void main(String[] args) {
		// This code is temporary fix - need to be resolved
		//System.setProperty("java.net.preferIPv4Stack", "true");
		//System.setProperty("java.net.preferIPv6Addresses", "false");
		System.setProperty("io.netty.resolver.dns.useJdkDnsServerAddressStreamProvider", "true");
		
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}