package com.algomeet.signalingservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserSessionBackupRequest {	
	/** Session ID for synchronization or verification **/
	@Size(max = 88, message = "{session.session-id.exceeded-max-size}") 
	private String sessionId;
	
	/** User key of remote user **/
	private UUID peerUserKey;
		
	/**
	 * Inbound/Outbound session.
	 * The pickled (encrypted serialized) session data
	 */
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 25000, message = "{session.encrypted-session.exceeded-max-size}") // 32 KB
	private String encryptedSession;
	
    /** true = inbound, false = outbound */
    private boolean inbound;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;
    
    /** The encryption algorithm, e.g. "OLM" */
    @Size(max = 24, message = "{session.algorithm.exceeded-max-size}") 
    private String algorithm;	
    
    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Size(max = 32, message = "{user-keys-backup.alg.exceeded-max-size}")
    private String aesAlg;
    
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10, message = "{user-keys-backup.version.exceeded-max-size}")
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Pattern(
        regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
        message = "{invalid-base64-format}"
    )
    @Size(max = 88, message = "{user-keys-backup.salt.exceeded-max-size}")
    private String salt;
}
