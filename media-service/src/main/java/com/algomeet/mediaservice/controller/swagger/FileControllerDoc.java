package com.algomeet.mediaservice.controller.swagger;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.MediaUploadResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "Media API", description = "Upload, download, share, and delete media files")
public interface FileControllerDoc {
    // ========================= UPLOAD =========================

    @Operation(
        summary = "Upload media file",
        description = "Uploads a media file. Storage backend depends on active configuration (LOCAL / S3 / OSS).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Upload successful",
                content = @Content(schema = @Schema(implementation = MediaUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Upload failed")
        }
    )
    public ResponseEntity<CommonResponse<MediaUploadResponse>> upload(
            @Parameter(description = "File to upload", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "User keys to share file with")
            @RequestParam(required = false)
            @ArraySchema(schema = @Schema(type = "string"))
            List<String> sharedWithUserKeys,

            @Parameter(description = "Override content type")
            @RequestParam(required = false)
            String contentType,

            @Parameter(description = "Whether file is encrypted")
            @RequestParam(required = false)
            Boolean encrypted
    ) throws Exception;

    // ========================= DOWNLOAD =========================

    @Operation(
        summary = "Get/Read media file",
        description = """
            Gets/Reads a media file.
            - LOCAL: returns file bytes
            - S3 / OSS: returns HTTP 302 redirect to a presigned URL
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "File get/read (LOCAL)"),
            @ApiResponse(responseCode = "302", description = "Redirect to presigned URL (S3 / OSS)"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    public ResponseEntity<?> getMedia(
            @Parameter(description = "Media ID", required = true)
            @PathVariable String mediaId
    );

    // ========================= DELETE =========================

    @Operation(
        summary = "Delete media file",
        description = "Soft deletes a media file. Deletes physical file only if orphaned.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Delete successful"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    public ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Media ID", required = true)
            @PathVariable String mediaId,

            @Parameter(description = "User keys whose access should also be removed")
            @RequestParam(required = false)
            List<String> deleteWithUserKeys
    );

    // ========================= SHARE =========================

    @Operation(
        summary = "Share media file",
        description = "Grants access to other users",
        responses = {
            @ApiResponse(responseCode = "200", description = "File shared successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    public ResponseEntity<?> share(
            @Parameter(description = "Media ID", required = true)
            @PathVariable String mediaId,

            @Parameter(description = "User keys to share with", required = true)
            @RequestParam List<String> shareWithUserKeys
    );
}
