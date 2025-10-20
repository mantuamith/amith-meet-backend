package com.algomeet.signalingservice.dto;

import lombok.Data;

@Data
public class OutboundSessionRequest {
	/**
	 * Outbound session.
	 * The pickled (encrypted serialized) session data
	 */
	private String encryptedSession;

	/** Group session ID for synchronization or verification **/
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;
    
    /** The encryption algorithm, e.g. "OLM" */
    private String algorithm;	
}
