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
		    message = "{invalid-base64-format}"
		)
	@Size(max = 25000, message = "{session.encrypted-session.exceeded-max-size}") // 32 KB
	private String encryptedSession;

	/** Session ID for synchronization or verification **/
	@Size(max = 88, message = "{session.session-id.exceeded-max-size}") 
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;
    
    /** The encryption algorithm, e.g. "OLM" */
    @Size(max = 24, message = "{session.algorithm.exceeded-max-size}") 
    private String algorithm;	
}
