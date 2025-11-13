package com.algomeet.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserE2eeSettingRequest {
    /**
     * Contains an encrypted key that used to decrypt the backup sessions, encrypted chat history, and etc.
     * This key can be decrypted using PIN, user password and etc.
     * 
     * 
     */
    private String syncKey;
    
    /**
     * Used to enable or disable the sessions backup synchronization, and etc.
     */
    private Boolean autoSyncEnabled;
}