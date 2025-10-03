package com.algomeet.notificationservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.notificationservice.dto.PushNotificationRequest;
import com.algomeet.notificationservice.response.CommonResponse;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Push Notifications", description = "APIs for sending push notifications to users")
public interface NotificationControllerDoc  {
    @Operation(
            summary = "Send push notification",
            description = "Publishes a push notification event to the message broker. "
                    + "If `id`, `senderId`, or `tenantId` are missing, they will be auto-populated."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification successfully published",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<? extends CommonResponse<?>> create(
            @RequestBody @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Push notification request payload",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = PushNotificationRequest.class),
                            examples = @ExampleObject(
                                    value = "{\n" +
                                    	    "  \"id\": \"550e8400-e29b-41d4-a716-446655440000\",\n" +
                                    	    "  \"type\": \"DIRECT_MESSAGE\",\n" +
                                    	    "  \"title\": \"New Chat Message\",\n" +
                                    	    "  \"body\": \"You have a new message from John\",\n" +
                                    	    "  \"senderId\": \"system-bot\",\n" +
                                    	    "  \"receiverIds\": [\"alice\", \"bob\"],\n" +
                                    	    "  \"data\": {\n" +
                                    	    "    \"chatId\": \"room-987\",\n" +
                                    	    "    \"priority\": \"HIGH\"\n" +
                                    	    "  },\n" +
                                    	    "  \"deliveryAckRequired\": true,\n" +
                                    	    "  \"tenantId\": 0\n" +
                                    	    "}"
                            )
                    )
            )
            PushNotificationRequest notificationRequest
    ) throws JsonProcessingException;

    @Operation(
            summary = "Send internal push notification",
            description = "Publishes a push notification event for internal system use. "
                    + "Behaves the same as `/notifications/push`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Internal notification successfully published",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<? extends CommonResponse<?>> internalCreate(
            @RequestBody @Valid PushNotificationRequest notificationRequest
    ) throws JsonProcessingException;
}
