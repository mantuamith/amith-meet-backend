package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.algomeet.xmpp.chatservice.enums.CallStatus;
import com.algomeet.xmpp.chatservice.enums.ParticipantCallStatus;

@Data
@Builder
@Document(collection = "call_sessions")
// 1. Existing: For state-machine updates
@CompoundIndex(name = "sid_status_idx", def = "{'sid': 1, 'status': 1}")

// 2. NEW: For covered queries on 1-on-1 vs MUC filtering
@CompoundIndex(name = "sid_roomId_idx", def = "{'sid': 1, 'roomId': 1}")

// 3. NEW: For ESR (Equality, Sort, Range) on the 'findFirst' query
@CompoundIndex(name = "sid_callee_created_idx", def = "{'sid': 1, 'callee': 1, 'createdAt': -1}")

// 4. NEW: High-performance cleanup for WebSocket disconnects
@CompoundIndex(name = "callerSid_status_idx", def = "{'callerSid': 1, 'status': 1}")

public class CallSession {
    @Id
    private String id;

    @Indexed
    private String sid;       // Maps to your Jingle 'sid'

    @Indexed
    private String caller;    // caller user key
    
    @Indexed
    private String callerSid; // caller websocket connection session ID

    @Indexed
    private String callee;    // callee user key
    
    @Indexed
    private String calleeSid;  // callee websocket connection session ID

    private String callType;  // "audio" or "video"
    
    private String roomId;    // Room/Group chat Id
    
    private CallStatus status;
    
    private ParticipantCallStatus callerStatus;
    
    private ParticipantCallStatus calleeStatus;
    
    private Integer tenantId;

    private Long createdAt;    // When the call invitation was sent
    private Long acceptedAt;   // When the callee clicked "Answer"
    private Long terminatedAt; // When the call ended (success/cancel/error)
}