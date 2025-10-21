package com.algomeet.signalingservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserKeysBackupRequest {
	@NotEmpty(message = "{user-keys-backup.create.empty-encrypted-account}")	
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "Invalid Base64 format"
		)
	@Size(max = 4500, message = "Base64 value too long") 
    private String encryptedAccount;	
	
	@Valid
	@Size(max = 10000, message = "Inbound sessions list cannot contain more than 10000 sessions")
	private List<Session> inboundSessions;
	
	@Valid
	@Size(max = 10000, message = "Outbound sessions list cannot contain more than 10000 sessions")
	private List<Session> outboundSessions;
	
	@Valid
	@Size(max = 10000, message = "Inbound group sessions list cannot contain more than 10000 sessions")
	private List<GroupSession> inboundGroupSessions;
	
	@Valid
	@Size(max = 10000, message = "Inbound group sessions list cannot contain more than 10000 sessions")
	private List<GroupSession> outboundGroupSessions;
	
	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Size(max = 10, message = "Version too long")
    private String version;

    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Size(max = 32, message = "Algorithm name too long")
    private String alg;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Pattern(
        regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
        message = "Salt must be valid Base64"
    )
    @Size(max = 88, message = "Salt too long")
    private String salt;
}