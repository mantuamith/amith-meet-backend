package com.algomeet.mediaservice.dto;

import java.util.List;
import java.util.Set;
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
	private Set<String> mediaIds;
	

    @NotNull(message = "The recipient list cannot be null")
    @NotEmpty(message = "You must provide at least one recipient user key")
    @Schema(
        description = "Recipient user keys that will be granted access to the media file."
    )
    private List<String> shareWithUserKeys;

    @Schema(
    	    description = "Required chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.",
    	    example = "share-019e537d-31a0-7556-a160-7ac448312343"
    	)
    @NotNull(message = "Chat message ID cannot be null")
    private UUID messageId;
}
