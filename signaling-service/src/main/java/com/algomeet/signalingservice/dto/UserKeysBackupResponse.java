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
    private String encryptedPrivateKey;		    
    private List<GroupSessionRequest> groupSessions;
    private Instant createdAt;
    private Instant updatedAt;
}
