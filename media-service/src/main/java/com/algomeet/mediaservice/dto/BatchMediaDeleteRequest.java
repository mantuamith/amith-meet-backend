package com.algomeet.mediaservice.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchMediaDeleteRequest {
	@Schema(
	    description = "List of media IDs to delete access"
	)
	private List<String> mediaIds;
	
	@Schema(
		    description = "List of user keys to be removed access"
		)
	private List<String> deleteWithUserKeys;
	
	@Schema(
    	    description = "Chat Message ID used to ensure idempotency and prevent duplicate processing of the same share request. "
    	    		+ "If a network failure occurs and the client retries the request, the same request ID should be reused.",
    	    example = "share-019e537d-31a0-7556-a160-7ac448312343"
    	)
    @NotNull(message = "Chat message ID cannot be null")
    private UUID messageId;
}
