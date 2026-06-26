package com.algomeet.mediaservice.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.algomeet.common.constant.DeleteMessageMediaFields;
import com.algomeet.common.properties.CommonRedisStreamProperties;
import com.algomeet.mediaservice.exceptions.UserFileNotFoundException;
import com.algomeet.mediaservice.service.UserFileService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
@Service
public class DeleteMessageMediaEventConsumer implements StreamListener<String, MapRecord<String, String, String>> {
	private final CommonRedisStreamProperties redisStreamProperties;	
	private final RedisConnectionFactory connectionFactory;
	private final UserFileService userFileService;	
	private final RedisTemplate<String, String> redisTemplate;
	
	private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
	
	private static final String GROUP_NAME = "message-media-delete-event-group"; // Static name for persistence
	private static final String consumerName = "consumer-" + UUID.randomUUID();
	
    private static final String LOCK_KEY = "lock:scheduler:process-pending:message-media-delete-event-group";
    
    private static final Integer RETRY_MAX = 3;
    
    // Lua script ensuring atomic "check-then-delete" lock releases to avoid cross-node lease hijacking
    private static final String RELEASE_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
    
    private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);
	private static final Duration CONSUMER_EVICTION_IDLE_TIME = Duration.ofDays(7);

	public DeleteMessageMediaEventConsumer(CommonRedisStreamProperties redisStreamProperties,
			RedisConnectionFactory connectionFactory, UserFileService userFileService,
			@Qualifier("streamStringRedisTemplate")
			RedisTemplate<String, String> redisTemplate) {
		this.redisStreamProperties = redisStreamProperties;
		this.connectionFactory = connectionFactory;
		this.userFileService = userFileService;
		this.redisTemplate = redisTemplate;
	}
	
	@PostConstruct
	public void init() {
	    String streamKey = redisStreamProperties.getMessageMediaDeleteEvents();

	    // Fix: Managed connection scope using try-with-resources
	    try (RedisConnection connection = connectionFactory.getConnection()) {
	        connection.streamCommands().xGroupCreate(
	                streamKey.getBytes(StandardCharsets.UTF_8),
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

	    container = StreamMessageListenerContainer.create(connectionFactory, options);

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
	    log.info("Received message from stream: {}", message.getId());
	    
	    String streamKey = redisStreamProperties.getMessageMediaDeleteEvents();
	    
	    // Scoped variables to ensure catch-block parsing safety
	    Set<String> fileIds = Set.of();
	    UUID messageId = null;
	    
	    try {
	        // 1. Safely Extract message content
	        Map<String, String> body = message.getValue();
	        if (body == null || body.isEmpty()) {
	            log.warn("Received empty stream message payload: {}. Acknowledging to clear.", message.getId());
	            acknowledgeAndPurge(streamKey, message);
	            return;
	        }

	        String userKey = body.get(DeleteMessageMediaFields.USER_KEY);
	        String mediaIdsStr = body.get(DeleteMessageMediaFields.MEDIA_IDS);
	        String deleteWithUserKeysStr = body.get(DeleteMessageMediaFields.DELETE_WITH_USER_KEYS);
	        String groupIdStr = body.get(DeleteMessageMediaFields.GROUP_ID);
	        String messageIdStr = body.get(DeleteMessageMediaFields.MESSAGE_ID);

	        // Parse structures cleanly
	        if (mediaIdsStr != null && !mediaIdsStr.isBlank()) {
	            fileIds = Arrays.stream(mediaIdsStr.split(","))
	                    .map(String::trim)
	                    .filter(id -> !id.isEmpty())
	                    .collect(Collectors.toSet());
	        }

	        Set<String> deleteWithUserKeys = deleteWithUserKeysStr != null 
	                ? Arrays.stream(deleteWithUserKeysStr.split(","))
	                        .map(String::trim)
	                        .filter(id -> !id.isEmpty())
	                        .collect(Collectors.toSet()) 
	                : Set.of();
	        
	        UUID groupId = groupIdStr != null ? UUID.fromString(groupIdStr) : null;
	        messageId = messageIdStr != null ? UUID.fromString(messageIdStr) : null;
	        boolean performedByAdmin = (userKey == null || userKey.isBlank());

	        // 2. Execute Business Logic Lifecycle
	        try {
	            if (performedByAdmin) {
	                userFileService.softDeleteAndMarkForCleanupIfOrphaned(fileIds, userKey, deleteWithUserKeys, groupId, messageId, performedByAdmin);
	            } else {
	                userFileService.softDeleteAndMarkForCleanupIfOrphaned(fileIds, userKey, deleteWithUserKeys, groupId, messageId);
	            }
	        } catch (UserFileNotFoundException ex) {
	            // Business fallback: Do not retry if the file simply doesn't exist anymore
	            log.warn("Media file(s) not found for stream message {} — acking to avoid infinite redelivery. fileIds={}", message.getId(), fileIds);                
	        } 

	        // 3. Successful transaction acknowledgment
	        acknowledgeAndPurge(streamKey, message);

	    } catch (Exception ex) {            
	        log.error("Failed to process stream message {}: {}", message.getId(), ex.getMessage(), ex);
	        
	        // Handle Distributed Retry Count instead of in-memory maps
	        handleRetryFallback(streamKey, message, fileIds, messageId);
	    }    
	}

	/**
	 * Clean helper abstraction to acknowledge and clear processed entries from the stream entirely.
	 */
	private void acknowledgeAndPurge(String streamKey, MapRecord<String, String, String> message) {
	    redisTemplate.opsForStream().acknowledge(GROUP_NAME, message);
	    redisTemplate.opsForStream().delete(streamKey, message.getId().getValue());
	    log.debug("Successfully acknowledged and purged message {} from stream context.", message.getId());
	}

	/**
	 * Handle retries. Note: Redis XPENDING naturally tracks delivery counter history natively.
	 * If you still prefer a dedicated key-based tracking framework, utilize Redis templates instead of JVM heap!
	 */
	private void handleRetryFallback(String streamKey, MapRecord<String, String, String> message, Set<String> fileIds, UUID messageId) {
	    try {
	        String trackableFileIds = fileIds != null ? fileIds.toString() : "UNKNOWN";
	        String trackableMsgId = messageId != null ? messageId.toString() : "UNKNOWN";
	        
	        // 1. Combine Native Stream ID with the sorted domain key to achieve 100% uniqueness
	        String uniqueDeduplicatedSuffix = getDeterministicContextKey(message.getId().getValue(), fileIds, messageId);
	        String redisRetryKey = "common:stream:delete-media:retry-count:" + uniqueDeduplicatedSuffix;
	        
	        // Increment retry count natively inside Redis server
	        Long count = redisTemplate.opsForValue().increment(redisRetryKey);
	        redisTemplate.expire(redisRetryKey, Duration.ofDays(1)); 

	        if (count != null && count > RETRY_MAX) {
	            log.error("Max retry thresholds reached for stream execution sequence. Aborting message context cleanly. Key={}, fileIds={}, messageId={}", 
	                    uniqueDeduplicatedSuffix, trackableFileIds, trackableMsgId);
	            
	            acknowledgeAndPurge(streamKey, message);
	            redisTemplate.delete(redisRetryKey); 
	        }
	    } catch (Exception structuralError) {
	        log.error("Critical Failure inside consumer fallback routing controller for message: {}", message.getId(), structuralError);
	    }
	}

	/**
	 * Generates a completely deterministic, null-safe compound key by sorting elements 
	 * alphabetically to neutralize any underlying JVM Set ordering variations.
	 */
	private String getDeterministicContextKey(String streamMessageId, Set<String> fileIds, UUID messageId) {
	    StringBuilder sb = new StringBuilder(streamMessageId);
	    
	    if (messageId != null) {
	        sb.append(":msg:").append(messageId);
	    }
	    
	    if (fileIds != null && !fileIds.isEmpty()) {
	        sb.append(":files:");
	        // Stream sort elements alphabetically so ["A", "B"] and ["B", "A"] produce the exact same key string format on all cluster nodes
	        String sortedFiles = fileIds.stream()
	                .filter(Objects::nonNull)
	                .sorted() 
	                .collect(Collectors.joining("_"));
	        sb.append(sortedFiles);
	    }
	    
	    return sb.toString();
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
	    String streamKey = redisStreamProperties.getMessageMediaDeleteEvents();
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

	    // Fix: Cleaned up manual null-checks by adopting try-with-resources
	    try (RedisConnection connection = connectionFactory.getConnection()) {
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
	    } catch (Exception ex) {
	        log.error("Failed to cleanly scan or evict idle consumers from group {}", GROUP_NAME, ex);
	    }
	}

	@PreDestroy
	public void destroy() {
	    if (container != null && container.isRunning()) {
	        container.stop();
	    }
	}
}