package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;
import com.algomeet.xmpp.chatservice.util.JidUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucCallTrackerService {

    private final CallTrackerRepository repository;
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final OfflineMessageService offlineMessageService;    
    private final JidUtil jidUtil;

    /**
     * Initiates the call record reactively.
     */
    public Mono<CallSession> trackInitiation(String sid, String caller, String callerSid, String callee, String callType, String roomId) {
        CallSession call = CallSession.builder()
                .sid(sid)
                .caller(caller)
                .callerSid(callerSid)
                .callee(callee)
                .callType(callType)
                .roomId(roomId)
                .createdAt(Instant.now().toEpochMilli())
                .build();

        return repository.save(call)
        		.doOnSuccess(success -> log.info("Call session {} successfully save", sid))
                .doOnError(error -> log.error("Failed to save call initiated with SID: {}", sid, error));
    }

    /**
     * Updates acceptance status. 
     * Uses flatMap to chain the lookup and the save.
     */
    public Mono<CallSession> trackAcceptance(String sid, String callee, String calleeSid) {
        return repository.findFirstBySidAndCalleeOrderByCreatedAtDesc(sid, callee)
                .flatMap(call -> {
                    call.setCalleeSid(calleeSid);
                    call.setAcceptedAt(Instant.now().toEpochMilli());
                    return repository.save(call);
                })
                .doOnSuccess(success -> log.info("Call session {} successfully updated to accepted", sid))
                .doOnError(error -> log.error("Failed to mark Call SID {} as ACCEPTED", sid, error))
                .switchIfEmpty(Mono.error(new RuntimeException("Call not found for SID: " + sid)));
    }
    
    /**
     * Remove the call record.
     */
    public Mono<Void> remove(String sid) {
        return repository.deleteBySid(sid)
        		.doOnSuccess(success -> log.info("Call session {} successfully deleted", sid))
                .doOnError(error -> log.error("Failed to delete Call SID {}", sid, error));
    }
    
    /**
     * Finalizes session, notifies parties, and then handles the document lifecycle.
     */
    public Mono<Void> finalizeAndNotify(String sid, String userSessionId, String reason) {
        return repository.findAllBySidAndRoomIdIsNotNull(sid) // Now returns Flux<CallSession>
                .flatMap(session -> {
                	
                	// Check the session ID
                	if(!((session.getCallerSid() != null && session.getCallerSid().equalsIgnoreCase(userSessionId)) 
                			|| (session.getCalleeSid() != null && session.getCalleeSid().equalsIgnoreCase(userSessionId)))) {                		
                	    return Mono.empty();
                	}
                	
                	// For group call only initiator or the caller can finalize the call
                	if (StringUtils.hasText(session.getRoomId()) 
                			&& !(session.getCallerSid() != null && session.getCallerSid().equalsIgnoreCase(userSessionId))) {
                	    return Mono.empty();
                	}

                    long now = Instant.now().toEpochMilli();

                    long duration = 0;
                    if (session.getAcceptedAt() != null && session.getAcceptedAt() > 0) {
                        duration = (now - session.getAcceptedAt()) / 1000;
                    }

                    String isoTimestamp = Instant.ofEpochMilli(now)
                            .atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_INSTANT);

                    // Logic check for status
                    String status = "success".equalsIgnoreCase(reason) ? "success" : "dropped";
                    
                    String callerJid = jidUtil.getBareJid(session.getCaller());
                    String calleeJid = jidUtil.getBareJid(session.getCallee());
                    
                    String callerMsgId = UUID.randomUUID().toString();
                    String calleeMsgId = UUID.randomUUID().toString(); // Corrected spelling
                    
                    // TODO: Don't send to caller for group chat call, call logs must be send by the group caller it self.                	
                    //String callerMsg = composeCallLogStanza(callerMsgId, callerJid, calleeJid, sid, session.getCallType(), duration, status, isoTimestamp);                    
                    //publish(callerMsgId, session.getCaller(), session.getCallee(), ChatType.CHAT, callerMsg);
                    
                    String calleeMsg = composeCallLogStanza(calleeMsgId, calleeJid, callerJid, sid, session.getCallType(), duration, status, isoTimestamp);
                    publish(calleeMsgId, session.getCallee(), session.getCaller(), ChatType.CHAT, calleeMsg);
                    
                    // We return the delete operation for this specific session
                    // If sid is shared across documents, this runs per document found
                    return repository.deleteBySid(sid);
                })
                .then() // Combines all inner publishers into a single Mono<Void>
                .doOnSuccess(v -> log.info("All call sessions for SID {} successfully finalized", sid))
                .doOnError(e -> log.error("Finalize failed for SID: {}", sid, e));
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
            "<message id='%s' to='%s' from='%s' type='groupchat'>" +
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
           
    public void reconcileDroppedCall(String userSessionId) {
        repository.findByCallerSidOrCalleeSid(userSessionId, userSessionId)
            .flatMap(callSession -> {
                log.info("Cleaning up abandoned call SID: {} for connection: {}", callSession.getSid(), userSessionId);
                
                if (callSession.getAcceptedAt() == null) {
                    // Call was never answered, just wipe it
                    return repository.deleteBySid(callSession.getSid()).then();
                }
                
                // Call was active, finalize with 'dropped' status
                return finalizeAndNotify(callSession.getSid(), userSessionId, "dropped");
            })
            .subscribe(
                success -> log.debug("Cleanup finished for session {}", userSessionId),
                error -> log.error("Cleanup error for session {}", userSessionId, error)
            );
    }
}