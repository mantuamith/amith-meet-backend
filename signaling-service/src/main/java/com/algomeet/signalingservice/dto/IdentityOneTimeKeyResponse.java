package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class IdentityOneTimeKeyResponse {	
    private UUID userKey;
    private Long id;
    private String oneTimekey;
    private Instant createdAt;
    private Instant updatedAt; 
    
	public IdentityOneTimeKeyResponse() {		
	}	
	
    public IdentityOneTimeKeyResponse(Long id, UUID userKey, String oneTimekey) {
		this.id = id;
		this.userKey = userKey;
		this.oneTimekey = oneTimekey;
	}   
}