package com.algomeet.mediaservice.dto;

import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchMediaDeleteRequest {	
	
	@Schema(
	    description = "List of media IDs to delete access"
	)
	@NotNull(message = "Media ID list cannot be null")
	@NotEmpty(message = "At least one media ID must be provided")
	private Set<String> mediaIds;
	
	@Schema(
			description = "List of user keys whose access should be revoked. To permanently remove a file when no access references remain, "
					+ "the media owner's user key must also be included in this list."
		)
	@NotNull(message = "User keys list cannot be null")
	@NotEmpty(message = "At least one User key must be provided")
	private Set<String> deleteWithUserKeys;
	
	@Schema(
    	    description = "Required chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.",
    	    example = "share-019e537d-31a0-7556-a160-7ac448312343"
    	)
    @NotNull(message = "Chat message ID cannot be null")
    private UUID messageId;
}
