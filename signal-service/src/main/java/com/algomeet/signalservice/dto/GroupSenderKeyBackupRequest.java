package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupSenderKeyBackupRequest {
    @NotBlank
    private String groupId;

    @NotNull
    private UUID distributionId;

    @NotBlank
    @Size(max = 300)
    @Pattern(
            regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
            message = "{invalid-base64-format}"
        )
    private String serializedSkdm;

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