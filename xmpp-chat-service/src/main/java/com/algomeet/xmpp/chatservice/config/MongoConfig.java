package com.algomeet.xmpp.chatservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "com.algomeet.xmpp.chatservice.repository")
public class MongoConfig {
}
