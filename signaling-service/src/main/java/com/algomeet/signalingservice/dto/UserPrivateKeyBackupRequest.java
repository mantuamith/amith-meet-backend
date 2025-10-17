package com.algomeet.signalingservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class UserPrivateKeyBackupRequest {
	@NotEmpty(message = "{user-private-key-backup.create.empty-private-key}")
    private String encryptedPrivateKey;
}