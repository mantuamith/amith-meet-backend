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
    private Boolean autoSyncEnabled;   
    private Boolean pinConfigured;
    private Boolean passcodeConfigured; 
    private Argon2Config argon2Config;
}