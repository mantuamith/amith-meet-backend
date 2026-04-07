package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document(collection = "call_sessions")
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
    private String calleSid;  // callee websocket connection session ID

    private String callType;  // "audio" or "video"

    private Long createdAt;    // When the call invitation was sent
    private Long acceptedAt;   // When the callee clicked "Answer"
    private Long terminatedAt; // When the call ended (success/cancel/error)
}