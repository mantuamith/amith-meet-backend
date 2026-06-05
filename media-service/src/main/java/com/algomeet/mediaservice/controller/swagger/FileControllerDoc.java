package com.algomeet.mediaservice.controller.swagger;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.BatchMediaDeleteRequest;
import com.algomeet.mediaservice.dto.BatchMediaShareRequest;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.UploadContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Media API", description = "Upload, read, share, and delete media files. Supports single-file and batch (multi-select album) uploads with image metadata extraction and chat-session association.")
public interface FileControllerDoc {

    // ========================= UPLOAD (single) =========================

    @Operation(
        summary = "Upload a single media file",
        description = """
            Uploads a single file (image, video, audio, or document) to the configured storage backend (LOCAL / S3 / OSS).

            **Camera flow**: Invoke after the user captures a photo or video from the in-app camera.
            Set `uploadContext=CHAT` and `conversationId` to associate the file with the active chat session.
            Image files return `mediaWidth` and `mediaHeight` in the response.

            **Storage accounting**:
            - `uploadContext=MEDIA` → counted against `mediaStorageUsed`
            - `uploadContext=CHAT`  → counted against `chatStorageUsed`

            **autoExpire behaviour**:
            - `true` (default): file is scheduled for deletion after the TTL if never shared.
              Use this for temporary preview uploads before the user confirms sending.
            - `false`: file is persisted and counted against storage quota immediately.
              Use this once the user confirms sending.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Upload successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MediaUploadResponse.class),
                    examples = @ExampleObject(name = "Image upload response", value = """
                        {
                          "code": "SUCCESS",
                          "message": "Success",
                          "data": {
                            "mediaId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                            "originalFilename": "camera_shot.jpg",
                            "contentType": "image/jpeg",
                            "size": 2048000,
                            "encrypted": false,
                            "url": "/media/3fa85f64-5717-4562-b3fc-2c963f66afa6",
                            "mediaWidth": 3024,
                            "mediaHeight": 4032,
                            "durationSeconds": null,
                            "conversationId": "conv_abc123"
                          }
                        }
                        """)
                )
            ),
            @ApiResponse(responseCode = "415", description = "File type not supported",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "MEDIA_FILE_TYPE_NOT_SUPPORTED", "message": "Media file type not supported" }
                    """))
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Upload failed")
        }
    )
    ResponseEntity<CommonResponse<MediaUploadResponse>> upload(
            @Parameter(description = "File to upload (image, video, audio, or document)", required = true)
            @RequestPart("file") MultipartFile file,

            @Parameter(description = "Override the detected MIME content type (optional)")
            @RequestParam(required = false) String contentType,

            @Parameter(description = "Set to true if the file payload is already client-side encrypted")
            @RequestParam(required = false) Boolean encrypted,

            @Parameter(
                description = "Whether the file should auto-expire based on the system TTL. " +
                    "Use `true` for preview-stage uploads; switch to `false` once the user confirms sending.",
                schema = @Schema(type = "boolean", defaultValue = "true", example = "true")
            )
            @RequestParam(required = false, defaultValue = "true") Boolean autoExpire,

            @Parameter(
                description = "Chat session / conversation ID to associate this file with. " +
                    "Required when the file is sent from Camera or Photos within a private chat.",
                example = "conv_abc123"
            )
            @RequestParam(required = false) String conversationId,

            @Parameter(
                description = "Storage quota bucket. Use `CHAT` when uploading chat attachments so the " +
                    "file counts against chatStorageUsed rather than mediaStorageUsed.",
                schema = @Schema(implementation = UploadContext.class, defaultValue = "MEDIA")
            )
            @RequestParam(required = false, defaultValue = "MEDIA") UploadContext uploadContext
    ) throws Exception;


    // ========================= UPLOAD (batch / album multi-select) =========================

    @Operation(
        summary = "Batch upload multiple files (album multi-select)",
        description = """
            Uploads multiple files in a single request. Designed for the **Photos (album selection)** flow
            where the user selects one or more images/videos from the device gallery.

            Files are processed independently — a single rejected file does not abort the others.

            **Response codes**:
            - `200 OK` — all files uploaded successfully.
            - `207 Multi-Status` — partial success; at least one file failed. The `data` array contains the successfully uploaded items.
            - `415 Unsupported Media Type` — all files rejected (type not supported).

            **Default behaviour**: `uploadContext` defaults to `CHAT` because batch uploads are almost always
            sent directly into a chat session. Override to `MEDIA` if needed.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "All files uploaded successfully",
                content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = MediaUploadResponse.class)),
                    examples = @ExampleObject(name = "All-success response", value = """
                        {
                          "code": "SUCCESS",
                          "message": "Success",
                          "data": [
                            {
                              "mediaId": "aaa11111-1111-1111-1111-aaaaaaaaaaaa",
                              "originalFilename": "IMG_001.jpg",
                              "contentType": "image/jpeg",
                              "size": 1536000,
                              "encrypted": false,
                              "url": "/media/aaa11111-1111-1111-1111-aaaaaaaaaaaa",
                              "mediaWidth": 4032,
                              "mediaHeight": 3024,
                              "durationSeconds": null,
                              "conversationId": "conv_abc123"
                            },
                            {
                              "mediaId": "bbb22222-2222-2222-2222-bbbbbbbbbbbb",
                              "originalFilename": "VID_002.mp4",
                              "contentType": "video/mp4",
                              "size": 15728640,
                              "encrypted": false,
                              "url": "/media/bbb22222-2222-2222-2222-bbbbbbbbbbbb",
                              "mediaWidth": null,
                              "mediaHeight": null,
                              "durationSeconds": null,
                              "conversationId": "conv_abc123"
                            }
                          ]
                        }
                        """)
                )
            ),
            @ApiResponse(responseCode = "207", description = "Partial success — some files failed",
                content = @Content(examples = @ExampleObject(value = """
                    {
                      "code": "MEDIA_BATCH_UPLOAD_PARTIAL_FAILURE",
                      "message": "One or more files failed to upload",
                      "data": [
                        {
                          "mediaId": "aaa11111-1111-1111-1111-aaaaaaaaaaaa",
                          "originalFilename": "IMG_001.jpg",
                          "contentType": "image/jpeg",
                          "size": 1536000,
                          "encrypted": false,
                          "url": "/media/aaa11111-1111-1111-1111-aaaaaaaaaaaa",
                          "mediaWidth": 4032,
                          "mediaHeight": 3024,
                          "durationSeconds": null,
                          "conversationId": "conv_abc123"
                        }
                      ]
                    }
                    """))
            ),
            @ApiResponse(responseCode = "415", description = "All files were of unsupported type")
        }
    )
    ResponseEntity<CommonResponse<List<MediaUploadResponse>>> uploadBatch(
            @Parameter(description = "List of files to upload (images and/or videos from album)", required = true)
            @RequestPart("files") List<MultipartFile> files,

            @Parameter(description = "Set to true if all file payloads are client-side encrypted")
            @RequestParam(required = false) Boolean encrypted,

            @Parameter(
                description = "Whether files should auto-expire. Use `true` for preview stage, `false` once confirmed for sending.",
                schema = @Schema(type = "boolean", defaultValue = "true", example = "true")
            )
            @RequestParam(required = false, defaultValue = "true") Boolean autoExpire,

            @Parameter(
                description = "Chat session / conversation ID to associate all uploaded files with.",
                example = "conv_abc123"
            )
            @RequestParam(required = false) String conversationId,

            @Parameter(
                description = "Storage quota bucket. Defaults to `CHAT` for batch album uploads.",
                schema = @Schema(implementation = UploadContext.class, defaultValue = "CHAT")
            )
            @RequestParam(required = false, defaultValue = "CHAT") UploadContext uploadContext
    ) throws Exception;


    // ========================= GET/READ =========================

    @Operation(
        summary = "Read / download a media file",
        description = """
            Fetches the media file content.

            - **LOCAL storage**: returns the file bytes inline (`Content-Disposition: inline`).
            - **S3 / OSS storage**: returns HTTP 302 redirect to a short-lived pre-signed URL.

            The caller must have `READ` permission on the file (owner or explicitly shared).
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "File bytes returned (LOCAL storage)"),
            @ApiResponse(responseCode = "302", description = "Redirect to pre-signed URL (S3 / OSS)"),
            @ApiResponse(responseCode = "403", description = "Access denied",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "MEDIA_ACCESS_DENIED", "message": "Media access denied" }
                    """))
            ),
            @ApiResponse(responseCode = "404", description = "Media not found",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "MEDIA_NOT_FOUND", "message": "Media not found" }
                    """))
            )
        }
    )
    ResponseEntity<?> getMedia(
            @Parameter(description = "Media ID returned by the upload endpoint", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID mediaId
    );


    // ========================= THUMBNAIL =========================

    @Operation(
        summary = "Get a thumbnail / preview of a media file",
        description = """
            Returns a scaled-down preview suitable for rendering inside a chat message bubble.

            **LOCAL storage + image files**: generates a server-side thumbnail scaled to `maxWidth` pixels (default 320).

            **S3 / OSS or video files**: returns HTTP 302 redirect to the full-size URL.
            Video thumbnail extraction requires native tooling (FFmpeg) which is not available in this service;
            the mobile client should derive a poster frame from the local capture session before upload.

            The caller must have `READ` permission on the file.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Thumbnail image bytes (LOCAL image files)"),
            @ApiResponse(responseCode = "302", description = "Redirect to full-size URL (S3 / OSS)"),
            @ApiResponse(responseCode = "404", description = "Thumbnail not available (non-image LOCAL file or media not found)",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "MEDIA_THUMBNAIL_NOT_AVAILABLE", "message": "Thumbnail not available for this media type" }
                    """))
            ),
            @ApiResponse(responseCode = "403", description = "Access denied")
        }
    )
    ResponseEntity<?> getThumbnail(
            @Parameter(description = "Media ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID mediaId,

            @Parameter(description = "Maximum thumbnail width in pixels", schema = @Schema(defaultValue = "320", example = "320"))
            @RequestParam(required = false, defaultValue = "320") int maxWidth
    );


    // ========================= DELETE =========================

    @Operation(
        summary = "Delete a media file",
        description = """
            Soft-deletes a media file. The physical file is removed only when it becomes orphaned
            (no remaining access control entries). Storage quota is decremented accordingly.

            Pass `deleteWithUserKeys` to simultaneously remove other users' access entries
            (e.g., when a chat message is retracted for all participants).
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "Delete successful",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "SUCCESS", "message": "Success" }
                    """))
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Media ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID mediaId,

            @Parameter(description = "Add owner's and additional user keys whose access should also be revoked (e.g., chat participants when retracting a message)")
            @RequestParam(required = true) Set<String> deleteWithUserKeys,
            
            @Parameter(description = "Chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.", required = true)
            @RequestParam UUID messageId
    );
    
    @Operation(
        summary = "Batch delete media files", 
        description = "Soft deletes specified media files and queues them for background cleanup if orphaned."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Media successfully processed",
            content = @Content(schema = @Schema(implementation = CommonResponse.class))
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Media file(s) not found",
            content = @Content(schema = @Schema(implementation = CommonResponse.class))
        )
    })
    public ResponseEntity<CommonResponse<?>> batchDelete(
            @RequestBody @Valid BatchMediaDeleteRequest request
    );


    // ========================= SHARE =========================

    @Operation(
        summary = "Share a media file with other users",
        description = """
            Grants READ, SHARE, and DELETE permissions on the file to the listed users.
            This is called automatically by the chat-service when a message containing media is delivered,
            so the recipient can access the attachment.

            The file's auto-expire cleanup timer is cleared once it has been shared.
            """,
        responses = {
            @ApiResponse(responseCode = "200", description = "File shared successfully",
                content = @Content(examples = @ExampleObject(value = """
                    { "code": "SUCCESS", "message": "Success" }
                    """))
            ),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<?> share(
            @Parameter(description = "Media ID", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID mediaId,

            @Parameter(description = "User keys (UUIDs) to share the file with", required = true,
                example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"660e8400-e29b-41d4-a716-446655440001\"]")
            @RequestParam List<String> shareWithUserKeys,
            
            @Parameter(description = "Chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.", required = true)
            @PathVariable UUID messageId
    );
    
    @Operation(
        summary = "Batch share media files",
        description = "Shares multiple media files with a list of users under the context of a specific chat message. Grants read, share, and delete permissions.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Files successfully shared"),
            @ApiResponse(responseCode = "403", description = "Access denied (caller does not have permission to share one or more files)"),
            @ApiResponse(responseCode = "404", description = "One or more media files were not found")
        }
    )
    public ResponseEntity<?> batchShare(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Details required for batch sharing media files", 
                required = true
            )
            @RequestBody @Valid BatchMediaShareRequest request
    );    
}
