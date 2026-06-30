package com.algomeet.mediaservice.controller.swagger;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.SessionDocumentDetailResponse;
import com.algomeet.mediaservice.dto.SessionDocumentSummaryResponse;
import com.algomeet.mediaservice.dto.SessionDocumentUploadForm;
import com.algomeet.mediaservice.dto.SessionDocumentUploadResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Document sharing history", description = "CRUD operations for shared documents")
@SecurityRequirement(name = "HttpBearerKey")
public interface SessionDocumentControllerDoc {

    @Operation(
            summary = "Return a list of metadata with pre-sign urls for download",
            description = "Used to get the list of files in past meetings",
            operationId = "retrieveContentSharingHistory_1"
    )
    @Parameters({
            @Parameter(name = "sessionId", description = "Session id", required = true),
            @Parameter(name = "offset", schema = @Schema(type = "integer", format = "int32", defaultValue = "0")),
            @Parameter(name = "page-size", schema = @Schema(type = "integer", format = "int32", defaultValue = "20"))
    })
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = SessionDocumentSummaryResponse.class))
            )
    )
    ResponseEntity<List<SessionDocumentSummaryResponse>> listDocuments(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(name = "page-size", defaultValue = "20") Integer pageSize
    );

    @Operation(
            summary = "Save an document and metadata",
            description = "Add a document to a meeting - allowed only to those with file-sharing feature if any",
            operationId = "addDocumentInMeeting"
    )
    @Parameter(name = "sessionId", description = "The session ID of the meeting.", required = true)
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "multipart/form-data",
                    schema = @Schema(implementation = SessionDocumentUploadForm.class),
                    encoding = {
                            @Encoding(name = "metadata", contentType = "application/json"),
                            @Encoding(name = "file", contentType = "application/octet-stream")
                    },
                    examples = @ExampleObject(
                            value = "{\"conferenceFullName\":\"room-name@conference.tenant.meet.example.com\",\"timestamp\":1741017572040,\"fileSize\":1042157,\"fileId\":\"e393a7e5-e790-4f43-836e-d27238201904\"}"
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "Document added successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SessionDocumentUploadResponse.class),
                    examples = @ExampleObject(value = "{\"fileId\":\"e393a7e5-e790-4f43-836e-d27238201904\"}")
            )
    )
    ResponseEntity<SessionDocumentUploadResponse> addDocument(
            @PathVariable String sessionId,
            @RequestPart("metadata") String metadata,
            @RequestPart("file") MultipartFile file
    );

    @Operation(
            summary = "Get file pre-signed URL plus document info",
            description = "Used by UI to get the presign url for the file before serving it to the user who needs it",
            operationId = "getDocumentInfoDuringMeeting"
    )
    @Parameters({
            @Parameter(name = "sessionId", required = true),
            @Parameter(name = "fileId", description = "The file ID to be deleted.", required = true)
    })
    @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = SessionDocumentDetailResponse.class)
            )
    )
    ResponseEntity<SessionDocumentDetailResponse> getDocumentInfo(
            @PathVariable String sessionId,
            @PathVariable String fileId
    );

    @Operation(hidden = true)
    ResponseEntity<Resource> downloadDocument(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            @RequestParam(required = false) String token
    ) throws java.io.IOException;

    @Operation(
            summary = "Delete a file by sessionId and fileId",
            description = "Delete a file by sessionId and fileId allowed by the file-upload feature",
            operationId = "deleteFile"
    )
    @Parameters({
            @Parameter(name = "sessionId", description = "The session ID of the meeting.", required = true, example = "86bf35e2-62a5-497e-9cae-efd35139f81f"),
            @Parameter(name = "fileId", description = "The file ID to be deleted.", required = true)
    })
    @ApiResponse(responseCode = "200", description = "OK")
    ResponseEntity<Void> deleteDocument(
            @PathVariable String sessionId,
            @PathVariable String fileId
    );
}
