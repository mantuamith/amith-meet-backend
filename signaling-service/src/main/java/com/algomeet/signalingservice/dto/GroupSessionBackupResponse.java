package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GroupSessionBackupResponse implements Serializable{
	private static final long serialVersionUID = 1L;	
	private UUID userKey;
	
	/** Group session ID for synchronization or verification **/
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Integer ratchetIndex;
	
	/** The group or room identifier */
    private Long groupId;
    
    /** User key of remote user **/
    private UUID peerUserKey;
    
    /** Session ID of remote user **/
	private String peerSessionId;
 
    /**
	 * Inbound/Outbound group session.
	 * The pickled (encrypted serialized) session data
	 */
	private String encryptedSession;

    /** The encryption algorithm, e.g. "MEGOLM" */
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
