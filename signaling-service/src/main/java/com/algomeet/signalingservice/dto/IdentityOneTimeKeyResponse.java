package com.algomeet.signalingservice.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class IdentityOneTimeKeyResponse {
	public IdentityOneTimeKeyResponse() {		
	}	
	
    public IdentityOneTimeKeyResponse(Long id, String identityKey, String oneTimekey) {
		super();
		this.id = id;
		this.identityKey = identityKey;
		this.oneTimekey = oneTimekey;
	}

	private Long id;
    private String identityKey;
    private String oneTimekey;
    private Instant createdAt;
    private Instant updatedAt;    
}