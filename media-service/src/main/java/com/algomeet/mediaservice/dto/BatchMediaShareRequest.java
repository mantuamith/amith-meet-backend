package com.algomeet.mediaservice.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchMediaShareRequest {
	@NotNull(message = "Media ID list cannot be null")
	@NotEmpty(message = "At least one media ID must be provided")
	@Schema(
	    description = "List of media IDs to grant access to."
	)
	private List<String> mediaIds;
	

    @NotNull(message = "The recipient list cannot be null")
    @NotEmpty(message = "You must provide at least one recipient user key")
    @Schema(
        description = "Recipient user keys that will be granted access to the media file."
    )
    private List<String> shareWithUserKeys;

    @Schema(
    	    description = "Chat Message ID used to ensure idempotency and prevent duplicate processing of the same share request. "
    	    		+ "If a network failure occurs and the client retries the request, the same request ID should be reused.",
    	    example = "share-019e537d-31a0-7556-a160-7ac448312343"
    	)
    @NotNull(message = "Chat message ID cannot be null")
    private UUID messageId;
}
