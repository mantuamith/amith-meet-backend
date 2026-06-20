package com.algomeet.notificationservice.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.properties.RedisStreamConfigProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {
	private final RedisStreamConfigProperties redisStreamConfigProperties;
	private final NotificationMessageHandler notificationConsumer;	
	private final RedisConnectionFactory connectionFactory;	
	private final RedisTemplate<String, String> redisTemplate;
	
	private static final String GROUP_NAME = "notification-group";
	private final String consumerName = "consumer-" + UUID.randomUUID();
	
	 private static final String LOCK_KEY = "lock:scheduler:process-pending:notification-group";
	 private static final Duration CONSUMER_EVICTION_IDLE_TIME = Duration.ofDays(7);
	    
	 // Lua script ensuring atomic "check-then-delete" lock releases to avoid cross-node lease hijacking
	 private static final String RELEASE_LUA_SCRIPT = 
			 "if redis.call('get', KEYS[1]) == ARGV[1] then " +
					 "    return redis.call('del', KEYS[1]) " +
					 "else " +
					 "    return 0 " +
					 "end";

	 final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);

    public NotificationStreamConsumer(
            RedisConnectionFactory connectionFactory,
            RedisStreamConfigProperties redisStreamConfigProperties,
            NotificationMessageHandler notificationConsumer,
            @Qualifier("notificationStringRedisTemplate")
            RedisTemplate<String, String> redisTemplate
    ) {
        this.connectionFactory = connectionFactory;
        this.redisStreamConfigProperties = redisStreamConfigProperties;
        this.notificationConsumer = notificationConsumer;
        this.redisTemplate = redisTemplate;
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
					true                  // create stream if not exists
					);
			log.info("Consumer group created: {} ", GROUP_NAME);
		} catch (Exception ex) {
			log.error("Error creating consumer group: {}, error {}", GROUP_NAME, 
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
		log.info("Consumer {}, thread-id: {}, received: {} ", consumerName, Thread.currentThread().getId(), message.getValue());

		try {
			notificationConsumer.handleMessage(message.getValue().get(Constants.REDIS_STREAM_MESSAGE_KEY_MESSAGE));
			
			// Proper acknowledgment using RecordId
			// 1. Acknowledge the message so it leaves the PEL (Pending Entries List)
	        byte[] streamKey = redisStreamConfigProperties.getNotificationStreamKey().getBytes();
	        connectionFactory.getConnection().xAck(streamKey, GROUP_NAME, message.getId());

	        // 2. Explicitly delete the message from the stream
	        connectionFactory.getConnection().xDel(streamKey, message.getId());

	        log.debug("Acknowledged and deleted message ID: {} ", message.getId());

		} catch (Exception ex) {
			log.error("Error processing message: {} - {}", message.getId(), ex.getMessage(), ex);
		}
	}
	
	/**
	 * Scheduled orchestrator task that periodically runs to scan for and claim abandoned 
	 * stream messages from dead or crashed consumer instances.
	 * * <p>To prevent concurrent execution across multiple microservice cluster nodes (the thundering 
	 * herd problem), this method leverages a Redis-backed distributed lock with an auto-expiry lease.
	 * The lock is safely released via a Lua script invocation to guarantee atomicity and prevent 
	 * premature accidental deletion if the pipeline execution outlives the lease window.</p>
	 */
	@Scheduled(fixedDelay = 30, timeUnit = java.util.concurrent.TimeUnit.MINUTES) // Run every 30 minutes
	public void claimAbandonedMessages() {		
		log.info("Check for abandoned notification messages.");
		String lockValue = UUID.randomUUID().toString();
        // Lease timeout window set to 15 minutes to guarantee adequate buffer room for batch loops
        long ttlMinutes = 15; 

        boolean acquired = false;
        try {
            // Attempt to acquire distributed lock (SET NX PX equivalent)
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes));
            
            acquired = Boolean.TRUE.equals(result);

            if (!acquired) {
                log.debug("Claim Abandoned Messages execution skipped: Another cluster node holds the scheduler lock key.");
                return;
            }

            log.info("Distributed lock acquired successfully [Token: {}]. Starting claim abandoned messages task...", lockValue);
            executeCleanupPipeline();

        } catch (Exception e) {
            log.error("Unexpected failure encountered during cleanup scheduling orchestration pipeline", e);
        } finally {
            if (acquired) {
                try {
                    // Execute atomic release validation via Redis engine
                    Long released = redisTemplate.execute(
                            new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
                            Collections.singletonList(LOCK_KEY),
                            lockValue
                    );

                    if (Long.valueOf(1L).equals(released)) {
                        log.info("Distributed lock safely released [Token: {}].", lockValue);
                    } else {
                        log.warn("Lock release bypassed: Lock lease expired or was overridden by another process context.");
                    }
                } catch (Exception ex) {
                    log.error("Failed to clean up lock key reference footprint from cache engine", ex);
                }
            }
        }
	}
	
	private void executeCleanupPipeline() {
	    String streamKey = redisStreamConfigProperties.getNotificationStreamKey();
	    log.debug("Checking for abandoned messages in group {}...", GROUP_NAME);

	    try {
	    	// Evict idle consumers
	    	evictIdleConsumers(streamKey);
	    	
	        // 1. Fetch the high-level summary overview
	        org.springframework.data.redis.connection.stream.PendingMessagesSummary pendingSummary = 
	                redisTemplate.opsForStream().pending(streamKey, GROUP_NAME);
	        
	        if (pendingSummary == null || pendingSummary.getTotalPendingMessages() == 0) {
	            return;
	        }

	     // Change List<PendingMessage> to PendingMessages
	        PendingMessages pendingEntries = 
	                redisTemplate.opsForStream().pending(
	                        streamKey, 
	                        GROUP_NAME, 
	                        Range.unbounded(), 
	                        500
	                );

	        if (pendingEntries == null || pendingEntries.isEmpty()) {
	            return;
	        }

	        // You can still iterate over it directly using an enhanced for-loop
	        for (PendingMessage pendingMsg : pendingEntries) {
	            // Avoid claiming tasks that this running instance already owns
	            if (consumerName.equals(pendingMsg.getConsumerName())) {
	                continue;
	            }

	            // If the message has been pending longer than 5 minutes, claim it
	            if (pendingMsg.getElapsedTimeSinceLastDelivery().compareTo(MAX_IDLE_TIME) > 0) {
	                log.info("Found abandoned message {} belonging to dead consumer {}. Claiming ownership...", 
	                        pendingMsg.getIdAsString(), pendingMsg.getConsumerName());

	                List<MapRecord<String, Object, Object>> claimedRecords = redisTemplate.opsForStream().claim(
	                        streamKey,
	                        GROUP_NAME,
	                        consumerName,
	                        MAX_IDLE_TIME,
	                        pendingMsg.getId()
	                );

	                if (claimedRecords != null && !claimedRecords.isEmpty()) {
	                    for (MapRecord<String, Object, Object> record : claimedRecords) {
	                        log.info("Successfully claimed message {}. Processing now...", record.getId());
	                        
	                        // 2. Extract the raw map values
	                        Map<Object, Object> rawMap = record.getValue();
	                        
	                        // 3. Convert the internal fields and values safely to String
	                        Map<String, String> stringMap = rawMap.entrySet().stream()
	                                .collect(Collectors.toMap(
	                                        e -> e.getKey().toString(),
	                                        e -> e.getValue() != null ? e.getValue().toString() : ""
	                                ));
	                        
	                        // 4. Wrap it in a type-safe String MapRecord
	                        MapRecord<String, String, String> stringRecord = MapRecord.create(
	                                record.getStream(), 
	                                stringMap
	                        ).withId(record.getId()); // Retain original Redis message ID
	                        
	                        this.onMessage(stringRecord);
	                    }
	                }
	            }
	        }
	    } catch (Exception ex) {
	        log.error("Error occurred during abandoned message sweeping: {}", ex.getMessage(), ex);
	    }
	}
	
	private void evictIdleConsumers(String streamKey) {
		byte[] streamKeyBytes = streamKey.getBytes(StandardCharsets.UTF_8);
	    RedisConnection connection = null;

	    try {
	        connection = connectionFactory.getConnection();
	        StreamInfo.XInfoConsumers consumers =
	                connection.streamCommands()
	                        .xInfoConsumers(streamKeyBytes, GROUP_NAME);

	        if (consumers == null || consumers.isEmpty()) {
	            return;
	        }

	        for (StreamInfo.XInfoConsumer consumerInfo : consumers) {

	            if (consumerName.equals(consumerInfo.consumerName())) {
	                continue;
	            }

	            if (consumerInfo.pendingCount() == 0
	                    && consumerInfo.idleTime().compareTo(CONSUMER_EVICTION_IDLE_TIME) > 0) {

	                log.info("Evicting dead consumer {}", consumerInfo.consumerName());

	                Boolean removed =
	                        connection.streamCommands()
	                                .xGroupDelConsumer(
	                                		streamKeyBytes,
	                                        GROUP_NAME,
	                                        consumerInfo.consumerName());

	                log.info(
	                        "Consumer {} removed from group {}, pending transferred: {}",
	                        consumerInfo.consumerName(),
	                        GROUP_NAME,
	                        removed);
	            }
	        }

	    } finally {
	        if (connection != null) {
	            connection.close();
	        }
	    }
	}
}