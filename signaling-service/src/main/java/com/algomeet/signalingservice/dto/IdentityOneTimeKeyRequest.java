package com.algomeet.signalingservice.dto;

import java.util.List;

import lombok.Data;

@Data
public class IdentityOneTimeKeyRequest {
    private String identityKey; 
    private List<String> oneTimeKeys;
}