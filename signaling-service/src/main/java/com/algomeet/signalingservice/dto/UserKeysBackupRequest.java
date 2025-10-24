package com.algomeet.signalingservice.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserKeysBackupRequest {
	private UUID userKey;
	
	@NotEmpty(message = "{user-keys-backup.create.empty-encrypted-account}")	
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
	@Size(max = 400000, message = "{user-keys-backup.encrypted-account.exceeded-max-size}")  // More than 256 KB
    private String encryptedAccount;	
	
	@Valid
	@Size(max = 5000, message = "{user-keys-backup.inbound-sessions.exceeded-max-size}") 
	private List<Session> inboundSessions;
	
	@Valid
	@Size(max = 5000, message = "{user-keys-backup.outbound-sessions.exceeded-max-size}") 
	private List<Session> outboundSessions;
	
	@Valid
	@Size(max = 5000, message = "{user-keys-backup.inbound-group-sessions.exceeded-max-size}") 
	private List<GroupSession> inboundGroupSessions;
	
	@Valid
	@Size(max = 5000, message = "{user-keys-backup.outbound-group-sessions.exceeded-max-size}")
	private List<GroupSession> outboundGroupSessions;
	
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10, message = "{user-keys-backup.version.exceeded-max-size}")
    private String version;

    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Size(max = 32, message = "{user-keys-backup.alg.exceeded-max-size}")
    private String alg;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Pattern(
        regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
        message = "{invalid-base64-format}"
    )
    @Size(max = 88, message = "{user-keys-backup.salt.exceeded-max-size}")
    private String salt;
}