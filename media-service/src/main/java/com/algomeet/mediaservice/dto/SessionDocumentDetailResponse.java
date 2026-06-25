package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Response body containing the document metadata")
public class SessionDocumentDetailResponse {

    @Schema(description = "File ID of the document", example = "e393a7e5-e790-4f43-836e-d27238201904")
    private String fileId;

    @Schema(description = "Session ID of the document", example = "85a32e37-ddd5-45de-89a6-e94ccffe547a")
    private String sessionId;

    @Schema(description = "Filename", example = "sample.pdf")
    private String fileName;

    @Schema(description = "Customer id", example = "vthtryv56yb65")
    private String customerId;

    @Schema(description = "User id", example = "dvdsvfhjv")
    private String userId;

    @Schema(description = "Pre-signed access url", example = "https://files.example.com/download?token=abc123")
    private String presignedUrl;

    @Schema(description = "Created at", format = "int64", example = "1745436546")
    private Long createdAt;

    @Schema(description = "File Size", format = "int64", example = "124")
    private Long fileSize;
}
