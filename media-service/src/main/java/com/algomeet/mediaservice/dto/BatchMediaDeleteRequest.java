package com.algomeet.mediaservice.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchMediaDeleteRequest {

	@NotNull(message = "Media ID list cannot be null")
	@NotEmpty(message = "At least one media ID must be provided")
	@Schema(
	    description = "List of media IDs to grant access to."
	)
	private List<String> mediaIds;
}
