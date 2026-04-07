package com.algomeet.signalservice.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class RedisTopicProperties {    
    @Value("${e2ee.event.topic:e2ee-event-topic}")
    private String e2eeEventTopic;
}
