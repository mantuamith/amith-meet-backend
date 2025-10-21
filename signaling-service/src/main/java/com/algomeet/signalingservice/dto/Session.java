package com.algomeet.signalingservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Session {
	private UUID userKey;
	/**
	 * Inbound/Outbound session.
	 * The pickled (encrypted serialized) session data
	 */
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "Invalid Base64 format"
		)
	@Size(max = 2000, message = "Base64 value too long") 
	private String encryptedSession;

	/** Session ID for synchronization or verification **/
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;
    
    /** The encryption algorithm, e.g. "OLM" */
    private String algorithm;	
}
