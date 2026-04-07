package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallTrackerService {

    private final CallTrackerRepository repository;
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final OfflineMessageService offlineMessageService;    
    private final DomainProperties domainProperties;

    /**
     * Initiates the call record reactively.
     */
    public Mono<CallSession> trackInitiation(String sid, String caller, String callerSid, String callee, String callType) {
        CallSession call = CallSession.builder()
                .sid(sid)
                .caller(caller)
                .callerSid(callerSid)
                .callee(callee)
                .callType(callType)
                .createdAt(Instant.now().toEpochMilli())
                .build();

        return repository.save(call)
                .doOnError(error -> log.error("Failed to save call initiated with SID: {}", sid, error));
    }

    /**
     * Updates acceptance status. 
     * Uses flatMap to chain the lookup and the save.
     */
    public Mono<CallSession> trackAcceptance(String sid, String calleeSid) {
        return repository.findBySid(sid)
                .flatMap(call -> {
                    call.setCalleSid(calleeSid);
                    call.setAcceptedAt(Instant.now().toEpochMilli());
                    return repository.save(call);
                })
                .doOnError(error -> log.error("Failed to mark Call SID {} as ACCEPTED", sid, error))
                .switchIfEmpty(Mono.error(new RuntimeException("Call not found for SID: " + sid)));
    }

    /**
     * Finalizes the call record.
     */
    public Mono<CallSession> trackTermination(String sid) {
        return repository.findBySid(sid)
                .flatMap(call -> {
                    call.setTerminatedAt(Instant.now().toEpochMilli());
                    return repository.save(call);
                })
                .doOnError(error -> log.error("Failed to mark Call SID {} as TERMINATED", sid, error));
    }
    
    /**
     * Remove the call record.
     */
    public Mono<Void> remove(String sid) {
        return repository.deleteBySid(sid)
                .doOnError(error -> log.error("Failed to delete Call SID {}", sid, error));
    }
    
    /**
     * Finalizes session, notifies parties, and then handles the document lifecycle.
     */
    public Mono<Void> finalizeAndNotify(String sid, String reason) {
        return repository.findBySid(sid)
                .flatMap(session -> {
                    long now = Instant.now().toEpochMilli();
                    session.setTerminatedAt(now);

                    long duration = 0;
                    if (session.getAcceptedAt() != null && session.getAcceptedAt() > 0) {
                        duration = (now - session.getAcceptedAt()) / 1000;
                    }

                    String isoTimestamp = Instant.ofEpochMilli(now)
                            .atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_INSTANT);

                    // Logic: If it's a connection drop and wasn't accepted, it's 'failed'
                    // Otherwise, if it was accepted but dropped, it's 'dropped'
                    String status = "success".equalsIgnoreCase(reason) ? "success" : "dropped";
                    String callerJid = session.getCaller() + "@" + domainProperties.getDomain();
                    String calleeJid = session.getCallee() + "@" + domainProperties.getDomain();
                    
                    String callerMsgId = UUID.randomUUID().toString();
                    String calleMsgId = UUID.randomUUID().toString();
                    
                    String callerMsg = composeCallLogStanza(callerMsgId, callerJid, calleeJid, sid, session.getCallType(), duration, status, isoTimestamp);
                    String calleMsg = composeCallLogStanza(calleMsgId, calleeJid, callerJid, sid, session.getCallType(), duration, status, isoTimestamp);

                    publish(callerMsgId, session.getCaller(), session.getCallee(), ChatType.CHAT, callerMsg);
                    publish(calleMsgId, session.getCallee(), session.getCaller(), ChatType.CHAT, calleMsg);
                    
                    // Instead of just doOnSuccess, we return the deletion Mono to the chain
                    return repository.deleteBySid(sid);
                })
                .doOnError(e -> log.error("Finalize failed for SID: {}", sid, e))
                .then(); // Return Mono<Void>
    }
    
    private void publish(String id, String to, String from, ChatType chatType, String payload) {    	
    	offlineMessageService.save(id, to, from, XmppMessageType.CHAT.getXmlValue(), payload)
        .doOnError(e -> {
            log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
        })
        .subscribe();
    	
    	// publish to cluster for synchronization
    	clusterMessagePublisher.convertAndSendToUser(id, to, from, chatType, payload);

    }

    private String composeCallLogStanza(String id, String to, String from, String sid, String type, long duration, String status, String timestamp) {
        // Constructing the final XML payload
        String stanza = String.format(
            "<message id='%s' to='%s' from='%s' type='chat'>" +
                "<body>%s call ended. Duration: %ds</body>" +
                "<call-log xmlns='https://algomeet.com/protocol/calls' " +
                          "sid='%s' " +
                          "type='%s' " +
                          "duration='%d' " +
                          "status='%s' " +
                          "timestamp='%s' />" +
            "</message>",
            id, to, from, capitalize(type), duration, sid, type, duration, status, timestamp
        );
        
        log.debug("Outbound XMPP Stanza: {}", stanza);
        
        return stanza;
    }

    private String capitalize(String str) {
        return (str == null || str.isEmpty()) ? "" : str.substring(0, 1).toUpperCase() + str.substring(1);
    }   
           
    public void reconcileDroppedCall(String sessionId) {
        repository.findByCallerSidOrCalleSid(sessionId, sessionId)
            .flatMap(callSession -> {
                log.info("Cleaning up abandoned call SID: {} for connection: {}", callSession.getSid(), sessionId);
                
                if (callSession.getAcceptedAt() == null) {
                    // Call was never answered, just wipe it
                    return repository.deleteBySid(callSession.getSid()).then();
                }
                
                // Call was active, finalize with 'dropped' status
                return finalizeAndNotify(callSession.getSid(), "dropped");
            })
            .subscribe(
                success -> log.debug("Cleanup finished for session {}", sessionId),
                error -> log.error("Cleanup error for session {}", sessionId, error)
            );
    }
}