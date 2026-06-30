package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Metadata structure that must be sent as JSON string in the metadata field")
public class SessionDocumentMetadataRequest {

    @NotBlank
    @Schema(
            type = "string",
            format = "uuid",
            description = "Client-generated unique identifier for the file",
            example = "e393a7e5-e790-4f43-836e-d27238201904"
    )
    private String fileId;

    @NotBlank
    @Schema(
            description = "Full name of the conference or meeting room",
            example = "room-name@conference.tenant.meet.example.com"
    )
    private String conferenceFullName;

    @NotNull
    @Schema(
            type = "integer",
            format = "int64",
            description = "Upload timestamp in milliseconds since epoch",
            example = "1741017572040"
    )
    private Long timestamp;

    @NotNull
    @Schema(
            type = "integer",
            format = "int64",
            description = "Size of the file in bytes",
            example = "1042157"
    )
    private Long fileSize;
}
