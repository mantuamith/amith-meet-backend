package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupSenderKeyRequest {
	@NotNull
    private UUID receiverUserKey;

	@NotNull(message = "receiverDeviceId is required")
    @Min(value = 1, message = "receiverDeviceId must be greater than 0")
    private Integer receiverDeviceId;

    /** Base64 or hex encoded SKDM ciphertext */
    @NotEmpty
    @Size(max = 2800)
    private String skdmCipher;
}