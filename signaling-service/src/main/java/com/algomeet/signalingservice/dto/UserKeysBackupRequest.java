package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class UserKeysBackupRequest {
	@NotEmpty(message = "{user-keys-backup.create.empty-encrypted-account}")
    private String encryptedAccount;	
	
	private List<Session> inboundSessions;
	
	private List<Session> outboundSessions;
	
	private List<GroupSession> inboundGroupSessions;
	
	private List<GroupSession> outboundGroupSessions;
}