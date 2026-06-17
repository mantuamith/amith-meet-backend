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
	
    @Schema(
        description = "User keys of the recipients to share the file(s) with. Typically used for one-to-one chats."
    )
    private List<String> shareWithUserKeys;
    
    @Schema(
            description = "ID of the group chat to share the file(s) with, adding all group members access simultaneously. Typically used for group chats."
        )
    private UUID groupId;

    @Schema(
    	    description = "Required chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.",
    	    example = "share-019e537d-31a0-7556-a160-7ac448312343"
    	)
    @NotNull(message = "Chat message ID cannot be null")
    private UUID messageId;
}
