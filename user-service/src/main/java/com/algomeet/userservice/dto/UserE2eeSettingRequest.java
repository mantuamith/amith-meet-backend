package com.algomeet.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserE2eeSettingRequest {    
    /**
     * Used to enable or disable the sessions backup synchronization, and etc.
     */
    private Boolean autoSyncEnabled;
    private Boolean pinConfigured;  
    /**
     * Used as flag if passcode was configured by the user.
     */
    private Boolean passcodeConfigured; 
    private Argon2Config argon2Config;
}