package com.algomeet.signalservice.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class MessageStatusUpdateRequest {   
	@NotEmpty(message = "The messageIds list must contain at least one ID.")
    private List<UUID> messageIds;

	@Schema(
			description = "Optional UTC timestamp in milliseconds since the epoch", 
			example = "1777749031000"
			)
	private Long date;
}