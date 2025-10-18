package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class UserKeysBackupRequest {
	@NotEmpty(message = "{user-private-key-backup.create.empty-private-key}")
    private String encryptedPrivateKey;	
	
	private List<GroupSessionRequest> groupSessions;
}