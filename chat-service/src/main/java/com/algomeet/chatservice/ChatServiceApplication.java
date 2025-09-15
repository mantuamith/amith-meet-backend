package com.algomeet.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = {
	    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
	    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
	})
@EnableFeignClients(basePackages = "com.algomeet.chatservice.client")
public class ChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
