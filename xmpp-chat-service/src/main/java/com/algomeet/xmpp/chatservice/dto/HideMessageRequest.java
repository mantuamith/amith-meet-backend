package com.algomeet.xmpp.chatservice.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request payload for hiding one or more chat messages for the current user's session.
 *
 * <p>This operation only hides the specified messages from the requesting user's
 * view. The messages remain available to other participants in the conversation.
 *
 * <h3>Example JSON</h3>
 * <pre>{@code
 * {
 *   "messageIds": [
 *     "2fc35cae-e0b7-40a5-b2aa-e86206730e99",
 *     "d6d8a4f4-4d58-4e90-8d4b-4a7d4f80b2d6"
 *   ],
 *   "sessionId": "e8c6d9b4-9f35-45d2-8cb5-2f1d9a0d8c1f"
 * }
 * }</pre>
 */
@Data
public class HideMessageRequest {

    /**
     * List of message IDs to hide.
     */
    @NotEmpty
    private List<UUID> messageIds;

    /**
     * Client session identifier used to synchronize the hide operation
     * across the user's active devices.
     */
    @Schema(
            description = "Client session identifier used to synchronize the hide operation across the user's active devices.",
            example = "e8c6d9b4-9f35-45d2-8cb5-2f1d9a0d8c1f"
        )
    @NotBlank
    private String sessionId;
}