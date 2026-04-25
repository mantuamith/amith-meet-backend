package com.algomeet.xmpp.chatservice.consumer;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.constant.MissedCallStream;
import com.algomeet.xmpp.chatservice.properties.RedisStreamProperties;
import com.algomeet.xmpp.chatservice.service.MissedCallService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class MissedCallConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    
    private final RedisStreamProperties redisStreamProperties;
    private final RedisConnectionFactory connectionFactory;
    private final MissedCallService missedCallService;
    
    // Inject the Reactive template to handle async XACK/XDEL
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final String GROUP_NAME = "missed-call-group"; // Static name for persistence
    private final String consumerName = "consumer-" + UUID.randomUUID();

    @PostConstruct
    public void init() {
        String streamKey = redisStreamProperties.getStreamMissedCallKey();
        
        // 1. Setup Group (Blocking is okay here as it only runs once at startup)
        try {
            connectionFactory.getConnection().xGroupCreate(
                    streamKey.getBytes(),
                    GROUP_NAME,
                    ReadOffset.from("0"),
                    true 
            );
        } catch (Exception ex) {
            // Error is expected if group already exists
            log.debug("Consumer group already exists or stream not initialized: {}", ex.getMessage());
        }

        // 2. Configure Imperative Listener Container
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(GROUP_NAME, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
        );

        container.start();
        log.info("Missed Call Consumer {} started on group {}", consumerName, GROUP_NAME);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        log.info("Received message: {}", message.getId());

        String streamKey = redisStreamProperties.getStreamMissedCallKey();

        // 3. Chain Reactive processing with Reactive Acknowledgment
        missedCallService.process(
                message.getValue().get(MissedCallStream.MESSAGE_KEY_MESSAGE), 
                message.getValue().get(MissedCallStream.MESSAGE_KEY_CHAT_TYPE)
            )
            .then(reactiveRedisTemplate.opsForStream().acknowledge(GROUP_NAME, message))
            .then(reactiveRedisTemplate.opsForStream().delete(streamKey, message.getId().getValue()))
            .doOnSuccess(v -> log.debug("Acknowledged and cleaned message {}", message.getId()))
            .doOnError(e -> log.error("Failed to process stream message {}: {}", message.getId(), e.getMessage()))
            // subscribe() is required here because the container is imperative
            .subscribe(); 
    }
}