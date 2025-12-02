package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SessionBackupRequest {	
	@NotNull
	@Min(value = 1, message = "registrationId must be greater than 0")
	private Integer registrationId;
    
    /** remote user's user key **/
	@NotNull
    private UUID remoteUserKey;
    
    /** Remote user's device ID **/
	@NotNull
	@Min(value = 1, message = "remoteDeviceId must be greater than 0")
    private Integer remoteDeviceId;
		
	/**
	 * Inbound/Outbound session.
	 * (encrypted serialized) session data
	 */
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 1500) 
    private String serializedSession; 
    
    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Size(max = 32, message = "{backup.aes-alg.exceeded-max-size}")
    private String aesAlg;
    
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10, message = "{backup.version.exceeded-max-size}")
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Pattern(
        regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
        message = "{invalid-base64-format}"
    )
    @Size(max = 88, message = "{backup.salt.exceeded-max-size}")
    private String salt;
}
