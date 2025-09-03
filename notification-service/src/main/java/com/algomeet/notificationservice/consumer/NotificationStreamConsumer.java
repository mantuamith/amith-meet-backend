package com.algomeet.notificationservice.consumer;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.properties.RedisStreamConfigProperties;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class NotificationStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {
	@Autowired
	private RedisStreamConfigProperties redisStreamConfigProperties;

	private static final String GROUP_NAME = "consumer-group-" + UUID.randomUUID();
	private final String consumerName = "consumer-" + System.currentTimeMillis();

	@Autowired
	private NotificationMessageHandler notificationConsumer;

	private final RedisConnectionFactory connectionFactory;

	public NotificationStreamConsumer(RedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@PostConstruct
	public void init() {
		// Try to create group if it doesn't exist
		try {
			connectionFactory.getConnection()
			.xGroupCreate(
					redisStreamConfigProperties.getNotificationStreamKey().getBytes(),
					GROUP_NAME,
					ReadOffset.from("0"),  // start at beginning
					true                   // create stream if not exists
					);
			log.info("Consumer group created: {} ", GROUP_NAME);
		} catch (Exception ex) {
			log.info("Error creating consumer group: {}, error {}", GROUP_NAME, 
					ex.getMessage(), ex);
		}

		// Configure listener container
		StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
				StreamMessageListenerContainer.StreamMessageListenerContainerOptions
				.builder()
				.pollTimeout(Duration.ofSeconds(2))
				.build();

		StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
				StreamMessageListenerContainer.create(connectionFactory, options);

		// Subscribe this consumer
		Subscription subscription = container.receive(
				Consumer.from(GROUP_NAME, consumerName),
				StreamOffset.create(redisStreamConfigProperties.getNotificationStreamKey(), ReadOffset.lastConsumed()),
				this
				);

		container.start();

		log.info("Consumer started: {} ", consumerName);
	}

	@Override
	public void onMessage(MapRecord<String, String, String> message) {
		// Update to debug when deploy to prod
		log.info("Consumer {} received: {} ", consumerName, message.getValue());

		try {
			notificationConsumer.handleMessage(message.getValue().get(Constants.REDIS_STREAM_MESSAGE_KEY_MESSAGE));
			// Proper acknowledgment using RecordId
			connectionFactory.getConnection()
			.xAck(redisStreamConfigProperties.getNotificationStreamKey().getBytes(), GROUP_NAME, message.getId());

			log.debug("Acknowledged message ID: {} ", message.getId());
		} catch (Exception ex) {
			log.error("Error processing message: {} - {}", message.getId(), ex.getMessage(), ex);
		}
	}
}