package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSession implements Serializable{
	private static final long serialVersionUID = 1L;	
	private UUID userKey;
	
	/** The group or room identifier */
    private Long groupId;
 
    /**
	 * Inbound/Outbound group session.
	 * The pickled (encrypted serialized) session data
	 */
	private String encryptedSession;

	/** Group session ID for synchronization or verification **/
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;

    /** The encryption algorithm, e.g. "MEGOLM" */
    private String algorithm;	
}
