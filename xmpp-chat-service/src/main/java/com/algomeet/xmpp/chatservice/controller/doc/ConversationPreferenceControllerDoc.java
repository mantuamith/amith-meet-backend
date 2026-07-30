package com.algomeet.xmpp.chatservice.controller.doc;


import java.util.List;

import org.springframework.http.ResponseEntity;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.ConversationPreferenceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Conversation Preferences", description = "Endpoints for retrieving user conversation settings (pin, mute, archive)")
public interface ConversationPreferenceControllerDoc {

	/**
	 * Retrieve all conversation preferences for the authenticated user.
	 */
	@Operation(
		summary = "Get user conversation preferences", 
		description = "Fetches all saved conversation preferences (pinned status, muted status, archive status) for the currently authenticated user."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Successfully retrieved conversation preferences",
			content = @Content(
				array = @ArraySchema(schema = @Schema(implementation = ConversationPreferenceResponse.class))
			)
		),
		@ApiResponse(
			responseCode = "401", 
			description = "Unauthorized - Invalid or missing authentication context",
			content = @Content
		),
		@ApiResponse(
			responseCode = "500", 
			description = "Internal Server Error",
			content = @Content
		)
	})
	public Mono<ResponseEntity<CommonResponse<List<ConversationPreferenceResponse>>>> getPreferences();	
}
