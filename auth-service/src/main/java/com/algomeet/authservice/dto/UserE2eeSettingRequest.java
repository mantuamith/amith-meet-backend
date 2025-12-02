package com.algomeet.authservice.dto;

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
    
    /**
     * Used as indicator if PIN was configured.
     */
    private Boolean pinConfigured;    
}