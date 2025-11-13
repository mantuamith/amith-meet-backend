package com.algomeet.authservice.dto;

import jakarta.validation.constraints.Size;
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
     */
    private String syncKey;
    
    /**
     * Used to enable or disable the sessions backup synchronization, and etc.
     */
    private Boolean autoSyncEnabled;
    
    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    private String algorithm;

    /** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10)
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Size(max = 88)
    private String salt;
}