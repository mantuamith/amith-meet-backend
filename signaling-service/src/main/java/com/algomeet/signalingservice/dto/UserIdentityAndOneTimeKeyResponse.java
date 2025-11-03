package com.algomeet.signalingservice.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserIdentityAndOneTimeKeyResponse {
    private UUID userKey;
    private String identityKey;
    private UserOneTimeKeyResponse oneTimeKey;
}