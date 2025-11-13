package com.algomeet.authservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserE2eeSettingResponse {
    private UUID userKey;
    private String syncKey;
    private Boolean autoSyncEnabled;
    private String algorithm;
    private String version;
    private String salt;
}