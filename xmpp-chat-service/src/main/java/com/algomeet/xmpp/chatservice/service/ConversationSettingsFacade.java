package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.stanza.SyncMessageRetentionStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingsFacade {

    private final ConversationSettingsCacheService conversationSettingsCacheService;
    private final ConversationSettingsService conversationSettingsService;
    private final ChatMessageRetentionLockManager chatMessageRetentionLockManager;
    private final ChatMessageService chatMessageService;
    private final JidUtil jidUtil;    
    private final ClusterMessagePublisher clusterMessagePublisher;

    public Mono<Void> updateMessageRetention(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
	    return Mono.defer(() -> {
	        // 1. ATOMIC ACQUIRE: Attempt to acquire the distributed lock
	        ChatMessageRetentionLockManager.LockToken lockToken = 
	                chatMessageRetentionLockManager.acquireLock(userKey, peerKey);

	        // If the token comes back null, the lock was not acquired (another process is running)
	        if (lockToken == null) {
	            return Mono.error(new IllegalStateException("Could not acquire retention update lock. Process already running."));
	        }

	        // 2. EXECUTE THE PIPELINE
	        return conversationSettingsService.saveOrUpdateRetentionDays(userKey, peerKey, messageRetentionDays)
	                .flatMap(savedSetting -> {	                	
	                	Integer retentionDays = (messageRetentionDays != -1) ? messageRetentionDays : null;	                	
	                	        		                	
	                	return conversationSettingsCacheService.evictSettings(userKey, peerKey)
	                			.then(chatMessageService.updatePurgeAtByToAndFrom(userKey, peerKey, retentionDays))
	                			.then(Mono.fromRunnable(() -> {
	                	            String messageId = UuidCreator.getTimeOrderedEpoch().toString();
	                	            
	                	            // Compose and send sync message to peer user and carbon copy to users online devices
	                	            SyncMessageRetentionStanza syncStanza = SyncMessageRetentionStanza.builder() 
	                	                    .id(messageId)
	                	                    .from(jidUtil.getBareJid(userKey.toString()))
	                	                    .to(jidUtil.getBareJid(peerKey.toString()))   
	                	                    .retentiondays(messageRetentionDays) // Note: ensure messageRetentionDays is accessible in this scope
	                	                    .type(XmppMessageType.HEADLINE.getXmlValue())
	                	                    .build();    
	                	            
	                	            clusterMessagePublisher.convertAndSendToUser(
	                	                    messageId,
	                	                    peerKey.toString(),
	                	                    userKey.toString(),
	                	                    ChatType.CHAT,
	                	                    false,
	                	                    true,
	                	                    false,
	                	                    syncStanza.toXml(),
	                	                    null);         
	                	        }));
	                })
	                .then()
	                // 3. SAFE RELEASE: Guarantees lock token release using the Lua script regardless of outcome
	                .doFinally(signalType -> chatMessageRetentionLockManager.releaseLock(lockToken));
	    });
	}
    
}