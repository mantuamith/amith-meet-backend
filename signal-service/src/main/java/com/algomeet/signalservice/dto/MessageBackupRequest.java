package com.algomeet.signalservice.dto;


import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageBackupRequest {   
	@NotNull
    private UUID stanzaId;	
	
	@NotNull
	private UUID messageId;
	
    @NotNull
	private UUID senderKey; 

	@NotNull
	private UUID receiverKey;     

	@NotEmpty
	@Size(max = 20000)
	@Pattern(
			regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
			message = "{invalid-base64-format}"
			)
	private String encryptedMessage; 

	private Long sentAt;

	private Long deliveredAt;

	private Long readAt;
	
    // Useful for finding all reactions, edits and etc to a specific message
    private UUID targetMessageId;    
    
    // Useful for finding all replies to a specific message
    private UUID replyToMessageId;  

	private Long size;
	
	/** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
	@Size(max = 32)
	private String algorithm;

	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
	@Size(max = 10)
	private String version;

	/** Base64-encoded salt value for key derivation (optional but recommended). */
	@Pattern(
			regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
			message = "Invalid base64 format"
			)
	@Size(max = 88)
	private String salt;
	
	private List<UUID> mediaIds;
}

