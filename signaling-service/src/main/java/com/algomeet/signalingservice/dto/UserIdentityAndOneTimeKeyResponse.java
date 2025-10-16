package com.algomeet.signalingservice.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserIdentityAndOneTimeKeyResponse {
    private UUID userKey;
    private String identityKey;
    private IdentityOneTimeKeyResponse oneTimeKey;
}