package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Response body containing the file metadata")
public class SessionDocumentSummaryResponse {

    @Schema(description = "Object id - can be file id", example = "e393a7e5-e790-4f43-836e-d27238201904")
    private String objectId;

    @Schema(description = "Session id", example = "85a32e37-ddd5-45de-89a6-e94ccffe547a")
    private String sessionId;

    @Schema(description = "Added timestamp", format = "int64", example = "124")
    private Long timestamp;

    @Schema(description = "Content type", example = "application/pdf")
    private String contentType;

    @Schema(description = "Object name", example = "sample.pdf")
    private String objectName;

    @Schema(description = "User id for the author", example = "f56g5y4")
    private String initiatorId;

    @Schema(description = "Pre-signed access url", example = "https://files.example.com/download?token=abc123")
    private String preSignedUrl;
}
