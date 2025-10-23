package com.algomeet.signalingservice.dto;

import java.io.Serializable;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 50000, message = "{group-session.encrypted-session.exceeded-max-size}") // 50KB
	private String encryptedSession;

	/** Group session ID for synchronization or verification **/
    @Size(max = 88, message = "{group-session.session-id.exceeded-max-size}") 
	private String sessionId;
	
	/** The current ratchet index for synchronization or verification */
    private Long ratchetIndex;

    /** The encryption algorithm, e.g. "MEGOLM" */
    @Size(max = 24, message = "{group-session.algorithm.exceeded-max-size}") 
    private String algorithm;	
}
