package com.algomeet.signalingservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserIdentityAndOneTimeKeyResponse {
    private String deviceId;
    private String identityKey;
    private UserOneTimeKeyResponse oneTimeKey;
}