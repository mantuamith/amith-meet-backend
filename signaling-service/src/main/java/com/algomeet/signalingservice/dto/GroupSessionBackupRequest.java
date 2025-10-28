package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupSessionBackupRequest implements Serializable{
	private static final long serialVersionUID = 1L;	
	
	/** 
	 * Important:
	 *   Inbound group session - this value must be same as sender outbound session ID
	 *   Outbound group session - this value must be the actual session ID 
	 */
    @Size(max = 88, message = "{group-session-backup.session-id.exceeded-max-length}") 
    @NotEmpty(message = "{group-session-backup.empty-session-id}")	
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Integer ratchetIndex;
	
	/** The group or room identifier */
    @NotNull(message = "{group-session-backup.empty-group-id}")	
    private Long groupId;
    
    /** User key of remote user **/
    private UUID peerUserKey;
 
    /**
	 * Inbound/Outbound group session.
	 * The pickled (encrypted serialized) session data
	 */
    @Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 50000, message = "{group-session-backup.encrypted-session.exceeded-max-size}") // 50KB
	private String encryptedSession;

    /** The encryption algorithm, e.g. "MEGOLM" */
    @Size(max = 24, message = "{group-session-backup.algorithm.exceeded-max-length}") 
    private String algorithm;    
	
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
