package com.algomeet.xmpp.chatservice.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;

import com.algomeet.xmpp.chatservice.document.SmBufferMessage;
import com.algomeet.xmpp.chatservice.properties.StreamManagementProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class responsible for maintaining MongoDB index integrity for the 
 * Stream Management (XEP-0198) buffer.
 * * <p>The primary role of this class is to ensure that unacknowledged stanzas stored in 
 * MongoDB do not persist indefinitely. By leveraging a TTL (Time-To-Live) index, the 
 * system automatically prunes orphaned session data that was not cleaned up during 
 * standard logout or resumption flows.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MongoSmBufferMessageIndexConfig {

	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final StreamManagementProperties properties;

    /**
     * Initializes required indexes once the ApplicationContext is fully refreshed 
     * and the application is ready to service requests.
     * * <p>This method uses {@link IndexOperations#ensureIndex(Index)} to check if the 
     * TTL index exists. If it does not exist, it will be created. If it exists but 
     * with different parameters, the behavior depends on the MongoDB driver version 
     * (usually requiring a manual drop to update TTL values).</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initIndicesAfterStartup() {
        log.info("Checking and initializing MongoDB indexes for SmBufferMessage...");

        try {
            ReactiveIndexOperations indexOps = reactiveMongoTemplate.indexOps(SmBufferMessage.class);
            
            /**
             * Define the TTL index on the 'createdAt' field.
             * * STRATEGY:
             * 1. .on("createdAt", Sort.Direction.ASC): Indexes the timestamp for efficient scanning.
             * 2. .expire(properties.getTtl()): Automatically deletes the document X seconds 
             * after the 'createdAt' time.
             * * Operational Note: The cleanup is performed by a background thread in MongoDB 
             * which runs approximately every 60 seconds.
             */
            Index ttlIndex = new Index()
                    .on("createdAt", Sort.Direction.ASC)
                    .expire(properties.getSession().getResumeTtl())
                    .named("sm_buffer_message_ttl_idx"); // Explicitly named for easier DB maintenance

            indexOps.ensureIndex(ttlIndex);
            
            log.info("Success: SM Buffer TTL index ensured with duration: {}", 
            		properties.getSession().getResumeTtl());
            
        } catch (Exception e) {
            log.error("Failed to initialize MongoDB indexes for SmBufferMessage. " +
                      "This may result in excessive database growth.", e);
            // In a production environment, you might want to stop the context if indices are critical
        }
    }
}