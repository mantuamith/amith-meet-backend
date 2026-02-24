package com.algomeet.mediaservice.controller.swagger;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "Internal Media API", description = "Delete media files")
public interface InternalFileControllerDoc {
	
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
    public void share(
            @Parameter(description = "Media ID", required = true)
            @PathVariable String mediaId,

            @Parameter(description = "User key")   
            @RequestParam String userKey,
            
            @Parameter(description = "User keys to share with", required = true)
            @RequestParam List<String> shareWithUserKeys
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
    public void delete(
            @Parameter(description = "Media ID", required = true)
            @PathVariable String mediaId,
            
            @Parameter(description = "User key")   
            @RequestParam String userKey,
            
            @Parameter(description = "User keys whose access should also be removed")           
            @RequestParam(required = false)
            List<String> deleteWithUserKeys
    );

}
