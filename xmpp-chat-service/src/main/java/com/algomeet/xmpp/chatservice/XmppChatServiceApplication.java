package com.algomeet.xmpp.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
	    scanBasePackages = {
	        "com.algomeet.xmpp.chatservice",
	        "com.algomeet.common"
	    }
	)
@EnableFeignClients(basePackages = "com.algomeet.xmpp.chatservice.client")
@EnableScheduling
public class XmppChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(XmppChatServiceApplication.class, args);
    }
}
