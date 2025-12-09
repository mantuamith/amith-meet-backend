package com.algomeet.notificationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.notificationservice.controller.swagger.UserNotificationControllerDoc;
import com.algomeet.notificationservice.enums.ResponseCode;
import com.algomeet.notificationservice.exceptions.RecordNotFoundException;
import com.algomeet.notificationservice.response.CommonResponse;
import com.algomeet.notificationservice.service.UserNotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications/user-notifications")
@RequiredArgsConstructor
public class UserNotificationController implements UserNotificationControllerDoc{

    private final UserNotificationService userNotificationService;

    // Get all notifications for a user
    @GetMapping("/user/{userKey}")
    public ResponseEntity<? extends CommonResponse<?>> getUserNotifications(
            @PathVariable String userKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ResponseEntity.ok(
        		CommonResponse.from(ResponseCode.SUCCESS, userNotificationService.getUserNotifications(userKey, page, size, sortBy, direction))
        );
    }
    
    // Get all notifications for a user
    @GetMapping("/user/{userKey}/unread")
    public ResponseEntity<? extends CommonResponse<?>> getUnreadNotifications(
            @PathVariable String userKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
    	
        return ResponseEntity.ok(
        		CommonResponse.from(ResponseCode.SUCCESS, userNotificationService.getUnreadNotifications(userKey, page, size, sortBy, direction))
        );
    }

    // Mark as read
    @PatchMapping("/{id}/read")
    public ResponseEntity<? extends CommonResponse<?>> markAsRead(@PathVariable Long id) {
    	try {
    		userNotificationService.markAsRead(id);
    	} catch (RecordNotFoundException ex) {
    		ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(
    				CommonResponse.from(ResponseCode.USER_NOTIFICATION_ID_NOT_FOUND));
    	}
    	
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }

    // Mark as delivered
    @PatchMapping("/{id}/delivered")
    public ResponseEntity<? extends CommonResponse<?>> markAsDelivered(@PathVariable Long id) {
    	try {
    		userNotificationService.markAsDelivered(id);

    	} catch (RecordNotFoundException ex) {
    		ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(
    				CommonResponse.from(ResponseCode.USER_NOTIFICATION_ID_NOT_FOUND));
    	}
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }

    // Delete user notification
    @DeleteMapping("/{id}")
    public ResponseEntity<? extends CommonResponse<?>> deleteUserNotification(@PathVariable Long id) {
    	try {
    		userNotificationService.deleteUserNotification(id);
    	} catch (RecordNotFoundException ex) {
    		ResponseEntity.status(HttpStatus.NOT_FOUND.value()).body(
    				CommonResponse.from(ResponseCode.USER_NOTIFICATION_ID_NOT_FOUND));
    	}
    	
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
}