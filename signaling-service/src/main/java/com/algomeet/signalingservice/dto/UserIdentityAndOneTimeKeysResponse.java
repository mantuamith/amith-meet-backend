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
    
    private List<UserIdentityAndOneTimeKeyResponse> keys;    
}