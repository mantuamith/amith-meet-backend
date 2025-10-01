package com.algomeet.notificationservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.notificationservice.response.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Notifications", description = "APIs for managing user notifications")
public interface UserNotificationControllerDoc  {

    @Operation(
            summary = "Get all notifications for a user",
            description = "Retrieve a paginated list of notifications for the given user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved notifications",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<? extends CommonResponse<?>> getUserNotifications(
            @Parameter(description = "Unique identifier of the user") @PathVariable String userKey,
            @Parameter(description = "Page number (default 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 500)") @RequestParam(defaultValue = "500") int size,
            @Parameter(description = "Field to sort by (default createdAt)") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc (default desc)") @RequestParam(defaultValue = "desc") String direction
    );

    @Operation(
            summary = "Get unread notifications for a user",
            description = "Retrieve a paginated list of unread notifications for the given user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved unread notifications",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<? extends CommonResponse<?>> getUnreadNotifications(
            @Parameter(description = "Unique identifier of the user") @PathVariable String userKey,
            @Parameter(description = "Page number (default 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 500)") @RequestParam(defaultValue = "500") int size,
            @Parameter(description = "Field to sort by (default updatedAt)") @RequestParam(defaultValue = "updatedAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc (default desc)") @RequestParam(defaultValue = "desc") String direction
    );

    @Operation(summary = "Mark notification as read", description = "Marks a specific notification as read by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<? extends CommonResponse<?>> markAsRead(
            @Parameter(description = "Notification ID") @PathVariable Long id);

    @Operation(summary = "Mark notification as delivered", description = "Marks a specific notification as delivered by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification marked as delivered"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<? extends CommonResponse<?>> markAsDelivered(
            @Parameter(description = "Notification ID") @PathVariable Long id);

    @Operation(summary = "Delete a user notification", description = "Deletes a specific user notification by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<? extends CommonResponse<?>> deleteUserNotification(
            @Parameter(description = "Notification ID") @PathVariable Long id);
}