package com.algomeet.xmpp.chatservice.document;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
	public static final String ID = "id";
	public static final String SID = "sid";
	public static final String CALLER = "caller";
	public static final String CALLER_SID = "callerSid";
	public static final String CALLEE = "callee";
	public static final String CALLEE_SID = "calleeSid";
	public static final String CALL_TYPE = "callType";
	public static final String ROOM_ID = "roomId";
	public static final String STATUS = "status";
	public static final String CALLER_STATUS = "callerStatus";
	public static final String CALLEE_STATUS = "calleeStatus";
	public static final String TENANT_ID = "tenantId";
	public static final String CREATED_AT = "createdAt";
	public static final String ACCEPTED_AT = "acceptedAt";
	public static final String TERMINATED_AT = "terminatedAt";
	public static final String UPDATED_AT = "updatedAt";
	
	@Id
	private String id;

	@Indexed
	@Field(SID)
	private String sid;       // Maps to your Jingle 'sid'

	@Indexed
	@Field(CALLER)
	private UUID caller;    // caller user key

	@Indexed
	@Field(CALLER_SID)
	private String callerSid; // caller websocket connection session ID

	@Indexed
	@Field(CALLEE)
	private UUID callee;    // callee user key

	@Indexed
	@Field(CALLEE_SID)
	private String calleeSid;  // callee websocket connection session ID

	@Field(CALL_TYPE)
	private String callType;  // "audio" or "video"

	@Field(ROOM_ID)
	private UUID roomId;    // Room/Group chat Id

	@Field(STATUS)
	private CallStatus status;

	@Field(CALLER_STATUS)
	private ParticipantCallStatus callerStatus;

	@Field(CALLEE_STATUS)
	private ParticipantCallStatus calleeStatus;

	@Field(TENANT_ID)
	private Integer tenantId;

	@Field(CREATED_AT)
	private Long createdAt;    // When the call invitation was sent

	@Field(ACCEPTED_AT)
	private Long acceptedAt;   // When the callee clicked "Answer"

	@Field(TERMINATED_AT)
	private Long terminatedAt; // When the call ended (success/cancel/error)

	@Field(UPDATED_AT)
	private Long updatedAt;    // Track structural modification/state changes	
	
	/**
	 * Optional: MongoDB TTL (Time To Live) index.
	 * Automatically deletes messages after 1 month
	 */
	@Indexed(expireAfterSeconds = 1 * 2592000) 
	private Instant expireAt;
}