package com.algomeet.xmpp.chatservice.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.enums.ChatType;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Orchestrator service responsible for routing missed call processing logic.
 * It acts as a bridge between the Redis Stream consumer and specific domain services
 * (Direct vs. MUC) based on the ChatType.
 */
@Slf4j
@Service
@AllArgsConstructor
public class MissedCallService {
    
    private final DirectMissedCallService directMissedCallService;
    private final MucMissedCallService mucMissedCallService;
    
    /**
     * Processes missed call notifications received from the message broker.
     * * @param message  For CHAT: A single SID. For GROUPCHAT: A comma-separated list of MUC SIDs.
     * @param chatType The string representation of the ChatType (e.g., "chat", "groupchat").
     * @return A Mono<Void> that completes when the specific domain service finishes processing.
     */
    public Mono<Void> process(String message, String chatType) {
        
        // 1. Structural Validation: Ensure we have both a routing type and a payload to process.
        if (chatType == null || !StringUtils.hasText(message)) {
            log.warn("Missed call process ignored: Missing chatType or empty message payload.");
            return Mono.empty();
        }

        try {
            // 2. Input Normalization: Convert string to Enum to ensure type-safe routing.
            ChatType type = ChatType.valueOf(chatType.toUpperCase());

            // 3. Routing Logic:
            if (ChatType.CHAT == type) {
                // Individual chat handles a single session ID.
                return directMissedCallService.loadMissedCalls(message);
                
            } else if (ChatType.GROUPCHAT == type) {
                // Group chat (MUC) handles batches. Split the CSV string into a list for processing.
                List<String> mucSids = Arrays.asList(message.split(","));
                
                log.debug("Routing {} MUC SIDs to MucMissedCallService", mucSids.size());
                return mucMissedCallService.loadMissedCalls(mucSids);
            }
            
        } catch (IllegalArgumentException e) {
            // Handle cases where chatType does not match ChatType enum values.
            log.error("Routing failed: Invalid chatType '{}' received for message: {}", chatType, message);
        }
        
        // Fallback for unsupported ChatTypes or caught exceptions.
        return Mono.empty();     
    }
}