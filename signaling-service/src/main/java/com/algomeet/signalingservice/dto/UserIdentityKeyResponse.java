package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserIdentityKeyResponse {
    private UUID userKey;
    private String identityKey;
    private Instant createdAt;
    private Instant updatedAt;
    private List<IdentityOneTimeKeyResponse> oneTimeKeys;
}