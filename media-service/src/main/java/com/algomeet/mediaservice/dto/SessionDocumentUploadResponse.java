package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Response body containing the fileId of the added document")
public class SessionDocumentUploadResponse {

    @Schema(description = "File ID of the added document", example = "e393a7e5-e790-4f43-836e-d27238201904")
    private String fileId;
}
