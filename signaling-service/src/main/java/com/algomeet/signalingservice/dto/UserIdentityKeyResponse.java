package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserIdentityKeyResponse {
    private String userKey;
    private String identityKey;
    private Instant createdAt;
    private Instant updatedAt;
    private List<IdentityOneTimeKeyResponse> oneTimeKeys;
}