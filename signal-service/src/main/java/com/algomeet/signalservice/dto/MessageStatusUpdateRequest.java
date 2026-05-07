package com.algomeet.signalservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageStatusUpdateRequest {
    @Size(max = 45)
    @Schema(description = "The stanzaId from the XMPP receipt to be used as the updateCursorId", example = "01JKH...")
    private String stanzaId;

    @Pattern(
        regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", 
        message = "Date must be in ISO 8601 format (YYYY-MM-DDThh:mm:ssZ)"
    )
    @Schema(description = "UTC timestamp in ISO 8601 format", example = "2026-05-02T19:10:31Z")
    private String date;
}