package com.algomeet.signalingservice.dto;

import java.util.List;

import lombok.Data;

@Data
public class UserIdentityKeyRequest {
    private String identityKey;
    private List<String> oneTimeKeys;
}