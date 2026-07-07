package com.algomeet.xmpp.chatservice.consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RStreamReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.StreamMessageId;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.constant.MissedCallFields;
import com.algomeet.xmpp.chatservice.properties.RedisStreamProperties;
import com.algomeet.xmpp.chatservice.service.MissedCallService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@RequiredArgsConstructor
@Service
public class MissedCallConsumer {    

    private final RedisStreamProperties redisStreamProperties;
    private final RedisConnectionFactory connectionFactory;
    private final MissedCallService missedCallService;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final RedissonReactiveClient redisson;

    private static final String GROUP_NAME = "missed-call-group"; 
    private final String consumerName = "consumer-" + UUID.randomUUID();
    private static final String LOCK_KEY = "xmpp:lock:scheduler:process-pending:missed-call-group";
    
    private static final String RELEASE_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
    
    final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);
    private static final Duration CONSUMER_EVICTION_IDLE_TIME = Duration.ofDays(7);

    @PostConstruct
    public void init() {
        String streamKey = redisStreamProperties.getMissedCall();
        
        // 1. Setup group safely using try-with-resources to guarantee the connection closes
        try (RedisConnection conn = connectionFactory.getConnection()) {
            conn.streamCommands().xGroupCreate(
                streamKey.getBytes(), 
                GROUP_NAME, 
                ReadOffset.from("0"), 
                true
            );
        } catch (Exception ex) {
            log.debug("Consumer group already exists: {}", ex.getMessage());
        }

        // 2. Start the non-blocking pull consumer loop
        startConsumerLoop(streamKey);
    }

    /**
     * Continuous reactive consumer loop pulling from Redis Stream.
     * Enforces backpressure and protects system memory under spikes of thousands of messages.
     */
    @SuppressWarnings("unchecked")
    private void startConsumerLoop(String streamKey) {
        Consumer consumer = Consumer.from(GROUP_NAME, consumerName);

        reactiveRedisTemplate.opsForStream()
            .read(consumer, StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
            .flatMap(message -> {
                // Safely cast standard stream record to targeted MapRecord
                MapRecord<String, String, String> record = (MapRecord<String, String, String>) (Object) message;
                
                // Concurrency throttling limit: Process up to 64 missed calls concurrently.
                // Anything higher remains safely queued inside Redis memory instead of filling up your JVM heap.
                return processMessagePayload(record);
            }, 64) 
            .doOnError(err -> log.error("Critical error in Missed Call Consumer subscription loop", err))
            // Keeps the listener loop resilient if your Redis cluster suffers an instantaneous failover
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))) 
            .subscribe(); 
    }

    /**
     * Reusable reactive pipeline that handles business logic processing, 
     * acknowledgement, and clean deletion of a stream message.
     */
    private Mono<Void> processMessagePayload(MapRecord<String, String, String> message) {
        String streamKey = redisStreamProperties.getMissedCall();
        log.info("Processing message via reactive loop: {}", message.getId());

        return missedCallService.process(
                message.getValue().get(MissedCallFields.MESSAGE), 
                message.getValue().get(MissedCallFields.CHAT_TYPE)
            )
            .then(reactiveRedisTemplate.opsForStream().acknowledge(GROUP_NAME, message))
            .then(reactiveRedisTemplate.opsForStream().delete(streamKey, message.getId().getValue()))
            .doOnSuccess(v -> log.debug("Acknowledged and cleaned message {}", message.getId()))
            .onErrorResume(e -> {
                log.error("Failed to process stream message {}: {}", message.getId(), e.getMessage());
                // Absorb error to avoid killing the parent stream processing loop
                return Mono.empty(); 
            })
            .then();
    }
    
    /**
     * Fully reactive cron task leveraging non-blocking distributed lock acquisition and release orchestration.
     */
    @Scheduled(fixedDelay = 30, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void processPendingMissedCalls() {		
        String lockValue = UUID.randomUUID().toString();
        long ttlMinutes = 5; 

        log.info("Attempting to acquire reactive lock for missed call recovery...");

        reactiveRedisTemplate.opsForValue()
            .setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes))
            .flatMap(acquired -> {
                if (Boolean.FALSE.equals(acquired)) {
                    log.debug("Claim Abandoned Messages skipped: Another cluster node holds the lock key.");
                    return Mono.empty();
                }

                log.debug("Distributed lock acquired [Token: {}]. Starting recovery loop...", lockValue);
                
                return executeCleanupPipeline()
                    .then(releaseLock(lockValue));
            })
            .onErrorResume(ex -> {
                log.error("Failure encountered during reactive cleanup pipeline execution", ex);
                return releaseLock(lockValue).then();
            })
            .block();
    }
	

    /**
     * Recover abandoned messages from dead consumers using Redis XAUTOCLAIM.
     */
    private Mono<Void> executeCleanupPipeline() {
        String streamKey = redisStreamProperties.getMissedCall();
        log.debug("Checking abandoned messages in group {}...", GROUP_NAME);

        ByteBuffer keyBuffer = ByteBuffer.wrap(streamKey.getBytes(StandardCharsets.UTF_8));

        // STAGE 1: Evict dead consumers using xInfoConsumers
        Mono<Void> consumerReaperStage =
                reactiveRedisTemplate.execute(conn -> conn.streamCommands().xInfoConsumers(keyBuffer, GROUP_NAME))
                // Ensure every consumerInfo is completely processed sequentially to prevent race conditions or connection hanging
                .concatMap(consumerInfo -> { 
                    if (consumerInfo.pendingCount() == 0 && consumerInfo.idleTime().compareTo(CONSUMER_EVICTION_IDLE_TIME) > 0) {
                        log.info("Evicting dead consumer {}", consumerInfo.consumerName());

                        return reactiveRedisTemplate.execute(conn -> 
                            conn.streamCommands().xGroupDelConsumer(keyBuffer, GROUP_NAME, consumerInfo.consumerName())
                        ).then();
                    }
                    return Mono.empty();
                })
                // Ensure the upstream Flux finishes emitting completely before discarding elements
                .then();
        
        // STAGE 2: Redisson AutoClaim logic
        RStreamReactive<String, String> stream = redisson.getStream(streamKey);
        
        Mono<Void> messageClaimStage = stream
            .autoClaim(
                GROUP_NAME,
                consumerName,
                MAX_IDLE_TIME.toMillis(),
                TimeUnit.MILLISECONDS,
                StreamMessageId.MIN,
                500
            )
            .flatMapMany(result -> Flux.fromIterable(result.getMessages().entrySet()))
            .map(entry -> MapRecord
                    .create(streamKey, entry.getValue())
                    .withId(RecordId.of(entry.getKey().toString())))
            .doOnNext(record -> log.info("Recovered abandoned message {}", record.getId()))
            .flatMap(this::processMessagePayload, 10)
            .then();

        return consumerReaperStage.then(messageClaimStage);
    }
       
    private Mono<Void> releaseLock(String lockValue) {
        return reactiveRedisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
                Collections.singletonList(LOCK_KEY),
                Collections.singletonList(lockValue)
            )
            .next()
            .doOnNext(released -> {
                if (Long.valueOf(1L).equals(released)) {
                    log.debug("Distributed lock safely released [Token: {}].", lockValue);
                } else {
                    log.warn("Lock release bypassed: Lock lease expired or was overridden.");
                }
            })
            .then();
    }
}