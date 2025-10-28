package com.algomeet.signalingservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserSessionBackupResponse {
	private UUID userKey;	
	private String sessionId;
	
	/** User key of remote user **/
	private UUID peerUserKey;
		
	/**
	 * Inbound/Outbound session.
	 * The pickled (encrypted serialized) session data
	 */
	private String encryptedSession;
	
    /** true = inbound, false = outbound */
    private boolean inbound;
    
    /** The encryption algorithm, e.g. "OLM" */
    private String algorithm;	
    
    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    private String aesAlg;
    
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    private String salt;
    
    private Instant createdAt;
    private Instant updatedAt;
}
