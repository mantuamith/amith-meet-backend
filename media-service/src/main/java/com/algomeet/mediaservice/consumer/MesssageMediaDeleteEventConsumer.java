package com.algomeet.mediaservice.consumer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.MessageMediaDeleteStream;
import com.algomeet.common.properties.CommonRedisStreamProperties;
import com.algomeet.mediaservice.exceptions.UserFileNotFoundException;
import com.algomeet.mediaservice.service.UserFileService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
/**
 * Redis Stream consumer responsible for processing message media deletion events.
 *
 * <p>
 * This consumer listens to the configured message media delete stream using a
 * Redis consumer group. For each event received, it performs soft deletion of
 * the associated media files and marks orphaned files for cleanup when they are
 * no longer referenced.
 * </p>
 *
 * <p>
 * Messages are processed with at-least-once delivery semantics:
 * </p>
 * <ul>
 *     <li>Consumes events through a Redis consumer group.</li>
 *     <li>Acknowledges messages only after successful processing.</li>
 *     <li>Removes acknowledged messages from the stream to prevent reprocessing.</li>
 *     <li>Supports both user-initiated and administrator-initiated deletions.</li>
 * </ul>
 *
 * <p>
 * A static consumer group name is used to preserve pending messages across
 * service restarts, while each application instance generates a unique consumer
 * name to allow multiple nodes to process events concurrently.
 * </p>
 */

@Slf4j
@RequiredArgsConstructor
@Service
public class MesssageMediaDeleteEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {

	@Autowired
	private CommonRedisStreamProperties redisStreamProperties;
	
	@Autowired
	private RedisConnectionFactory connectionFactory;
	
	@Autowired
	private UserFileService userFileService;

	@Autowired
	@Qualifier("deleteMediaStringRedisTemplate")
	private RedisTemplate<String, String> redisTemplate;

	private static final String GROUP_NAME = "message-media-delete-event-group"; // Static name for persistence
	private final String consumerName = "consumer-" + UUID.randomUUID();

	@PostConstruct
	public void init() {
		String streamKey = redisStreamProperties.getMessageMediaDeleteEvents();

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
		log.info("Message media delete event consumer {} started on group {}", consumerName, GROUP_NAME);
	}

	@Override
	public void onMessage(MapRecord<String, String, String> message) {
		log.info("Received message: {}", message.getId());
		
		try {
			String streamKey = redisStreamProperties.getMessageMediaDeleteEvents();

			// Retrieve message content
			String userKey = message.getValue().get(MessageMediaDeleteStream.MESSAGE_KEY_USER_KEY);
			String mediaIdsStr = message.getValue().get(MessageMediaDeleteStream.MESSAGE_KEY_MEDIA_IDS);
			String deleteWithUserKeysStr = message.getValue().get(MessageMediaDeleteStream.MESSAGE_KEY_DELETE_WITH_USER_KEYS);
			String groupIdStr = message.getValue().get(MessageMediaDeleteStream.MESSAGE_KEY_GROUP_ID);
			String messageIdStr = message.getValue().get(MessageMediaDeleteStream.MESSAGE_KEY_MESSAGE_ID);

			Set<String> fileIds = mediaIdsStr != null 
					? Arrays.stream(mediaIdsStr.split(","))
							.map(String::trim)
							.filter(id -> !id.isEmpty())
							.collect(Collectors.toSet()) 
							: Set.of();

			Set<String> deleteWithUserKeys = deleteWithUserKeysStr != null 
					? Arrays.stream(deleteWithUserKeysStr.split(","))
							.map(String::trim)
							.filter(id -> !id.isEmpty())
							.collect(Collectors.toSet()) 
							: Set.of();
			
			UUID groupId = groupIdStr != null ? UUID.fromString(groupIdStr) : null;
			UUID messageId = messageIdStr != null ? UUID.fromString(messageIdStr) : null;
			boolean performedByAdmin = userKey == null;

			try {
				if(performedByAdmin) {
					userFileService.softDeleteAndMarkForCleanupIfOrphaned(fileIds, userKey,  deleteWithUserKeys, groupId, messageId, performedByAdmin);
				} else {
					userFileService.softDeleteAndMarkForCleanupIfOrphaned(fileIds, userKey,  deleteWithUserKeys, groupId, messageId);
				}
			} catch (UserFileNotFoundException ex) {
				log.warn("User file(s) not found {} ", fileIds, ex);
			} 

			redisTemplate.opsForStream().acknowledge(GROUP_NAME, message);
			redisTemplate.opsForStream().delete(streamKey, message.getId().getValue());

			log.debug("Acknowledged and cleaned message {}", message.getId());

		} catch (Exception ex) {
			log.error("Failed to process stream message {}: {}", message.getId(), ex.getMessage(), ex);
		}    
	}
}