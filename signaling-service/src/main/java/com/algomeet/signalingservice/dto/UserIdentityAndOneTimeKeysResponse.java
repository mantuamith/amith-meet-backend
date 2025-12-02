package com.algomeet.signalingservice.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class UserIdentityAndOneTimeKeysResponse {
    private UUID userKey;
    
    @Deprecated // deprecated to improve context meaning
    private List<UserIdentityAndOneTimeKeyResponse> keys;   
    
    private List<UserIdentityAndOneTimeKeyResponse> devices;

    @Deprecated
	public List<UserIdentityAndOneTimeKeyResponse> getKeys() {
		return devices;
	}

	@Deprecated
	public void setKeys(List<UserIdentityAndOneTimeKeyResponse> keys) {
		this.devices = keys;
	}    
}