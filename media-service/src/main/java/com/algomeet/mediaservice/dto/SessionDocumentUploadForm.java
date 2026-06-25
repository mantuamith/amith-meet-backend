package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SessionDocumentUploadForm", requiredProperties = { "metadata", "file" })
public class SessionDocumentUploadForm {

    @Schema(
            description = "The metadata of the document in JSON format. Must conform to SessionDocumentMetadataRequest schema.",
            example = "{\"conferenceFullName\":\"room-name@conference.tenant.meet.example.com\",\"timestamp\":1741017572040,\"fileSize\":1042157,\"fileId\":\"e393a7e5-e790-4f43-836e-d27238201904\"}"
    )
    private String metadata;

    @Schema(description = "The file to be uploaded.", type = "string", format = "binary")
    private String file;
}
