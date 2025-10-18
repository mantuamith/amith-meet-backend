package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UserOneTimeKeyResponse {	
    private UUID userKey;
    private Long id;
    private String key;
    private Instant createdAt;
    private Instant updatedAt; 
    private Boolean used;
    
	public UserOneTimeKeyResponse() {		
	}	
	
    public UserOneTimeKeyResponse(Long id, UUID userKey, String oneTimekey) {
		this.id = id;
		this.userKey = userKey;
		this.key = oneTimekey;
	}   
}