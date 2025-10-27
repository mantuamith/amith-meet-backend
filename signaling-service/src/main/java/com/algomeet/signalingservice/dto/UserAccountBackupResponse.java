package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserAccountBackupResponse {
	private UUID userKey;
    private String encryptedAccount;		
	    
    private String aesAlg;
    private String version;
    private String salt;
	
    private Instant createdAt;
    private Instant updatedAt;
}
