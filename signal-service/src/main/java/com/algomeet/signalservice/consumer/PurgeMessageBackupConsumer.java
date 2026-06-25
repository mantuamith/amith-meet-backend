package com.algomeet.signalservice.consumer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.algomeet.signalservice.constant.PurgeBackupMessageFields;
import com.algomeet.signalservice.properties.RedisStreamProperties;
import com.algomeet.signalservice.service.MessageBackupService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class PurgeMessageBackupConsumer implements StreamListener<String, MapRecord<String, String, String>> {
	@Autowired
	private RedisStreamProperties redisStreamProperties;
	
	@Autowired
	private RedisConnectionFactory connectionFactory;
	
	@Autowired
	private MessageBackupService messageBackupService;

	@Autowired
	@Qualifier("streamStringRedisTemplate")
	private RedisTemplate<String, String> redisTemplate;

	private static final String GROUP_NAME = "purge-message-backup-group"; // Static name for persistence
	private final String consumerName = "consumer-" + UUID.randomUUID();
	
    private static final String LOCK_KEY = "lock:scheduler:process-pending:purge-message-backup-group";
    
    // Lua script ensuring atomic "check-then-delete" lock releases to avoid cross-node lease hijacking
    private static final String RELEASE_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
    
    private final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);
	private static final Duration CONSUMER_EVICTION_IDLE_TIME = Duration.ofDays(7);

	@PostConstruct
	public void init() {
		String streamKey = redisStreamProperties.getPurgeMessageBackup();

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
		log.info("Message message backup consumer {} started on group {}", consumerName, GROUP_NAME);
	}

	@Override
	public void onMessage(MapRecord<String, String, String> message) {
		log.info("Received message: {}", message.getId());
		
		try {
			String streamKey = redisStreamProperties.getPurgeMessageBackup();

			// Retrieve message content
			String userKey = message.getValue().get(PurgeBackupMessageFields.USER_KEY);

			if (StringUtils.isEmpty(userKey)) {
				messageBackupService.purgeMessageBackup(UUID.fromString(userKey));
			}

			redisTemplate.opsForStream().acknowledge(GROUP_NAME, message);
			redisTemplate.opsForStream().delete(streamKey, message.getId().getValue());

			log.debug("Acknowledged and cleaned message {}", message.getId());

		} catch (Exception ex) {
			log.error("Failed to process stream message {}: {}", message.getId(), ex.getMessage(), ex);
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
	@Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS) // Run every 60 minutes
	public void claimAbandonedMessages() {		
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
	    String streamKey = redisStreamProperties.getPurgeMessageBackup();
	    log.debug("Checking for abandoned messages in group {}...", GROUP_NAME);

	    // Evict idle consumers
	    evictIdleConsumers(streamKey);
	    try {
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

