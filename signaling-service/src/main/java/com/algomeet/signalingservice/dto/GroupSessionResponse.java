package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class GroupSessionResponse implements Serializable{
	private static final long serialVersionUID = 1L;	
	/** The group or room identifier */
    private Long groupId;
 
    /**
	 * Out bound group session.
	 * The pickled (encrypted serialized) session data
	 */
	private String encryptedOutboundSession;

	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;

    /** The encryption algorithm, e.g. "MEGOLM" */
    private String algorithm;	
	
	private List<InboundGroupSessionKey> inboundSessionKeys;
}
