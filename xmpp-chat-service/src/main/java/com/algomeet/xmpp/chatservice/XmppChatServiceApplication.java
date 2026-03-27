package com.algomeet.xmpp.chatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.algomeet.xmpp.chatservice.client")
public class XmppChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(XmppChatServiceApplication.class, args);
    }
}
