package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupSenderKeyAckRequest {
	@NotNull
    private UUID senderUserKey;

	@NotNull(message = "senderDeviceId is required")
    @Min(value = 1, message = "senderDeviceId must be greater than 0")
    private Integer senderDeviceId;
}