package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserKeysBackupResponse {
	private UUID userKey;
    private String encryptedAccount;		
		
	private List<Session> inboundSessions;	
	private List<Session> outboundSessions;
	
	private List<GroupSession> inboundGroupSessions;	
	private List<GroupSession> outboundGroupSessions;
	
    private String version;
    private String alg;
    private String salt;
	
    private Instant createdAt;
    private Instant updatedAt;
}
