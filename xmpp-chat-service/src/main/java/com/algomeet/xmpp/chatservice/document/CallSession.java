package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.algomeet.xmpp.chatservice.enums.CallStatus;
import com.algomeet.xmpp.chatservice.enums.ParticipantCallStatus;

@Data
@Builder
@Document(collection = "call_sessions")
@CompoundIndexes({
	// 1. Covers: findAllBySidAndRoomIdIsNull, findAllBySidAndRoomIdIsNotNull, 
	//            findFirstBySidAndRoomIdIsNotNull, deleteBySid, and state updates matching sid/status.
	@CompoundIndex(name = "idxCall_sid_roomId_createdAtDesc", def = "{'sid': 1, 'roomId': 1, 'createdAt': -1}"),

	// 2. Covers: findFirstBySidAndCalleeOrderByCreatedAtDesc AND deleteBySidAndCallee
	@CompoundIndex(name = "idxCall_sid_callee_createdAtDesc", def = "{'sid': 1, 'callee': 1, 'createdAt': -1}"),

	// 3. Covers: First leg of findByCallerSidOrCalleeSid AND callerSid WebSocket cleanups
	@CompoundIndex(name = "idxCall_callerSid_status", def = "{'callerSid': 1, 'status': 1}"),

	// 4. Covers: Second leg of findByCallerSidOrCalleeSid (Prevents full collection scans on $or)
	@CompoundIndex(name = "idxCall_calleeSid_status", def = "{'calleeSid': 1, 'status': 1}")
})

public class CallSession {
	@Id
	private String id;

	@Indexed
	private String sid;       // Maps to your Jingle 'sid'

	@Indexed
	private UUID caller;    // caller user key

	@Indexed
	private String callerSid; // caller websocket connection session ID

	@Indexed
	private UUID callee;    // callee user key

	@Indexed
	private String calleeSid;  // callee websocket connection session ID

	private String callType;  // "audio" or "video"

	private UUID roomId;    // Room/Group chat Id

	private CallStatus status;

	private ParticipantCallStatus callerStatus;

	private ParticipantCallStatus calleeStatus;

	private Integer tenantId;

	private Long createdAt;    // When the call invitation was sent
	private Long acceptedAt;   // When the callee clicked "Answer"
	private Long terminatedAt; // When the call ended (success/cancel/error)
}