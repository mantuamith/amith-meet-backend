package com.algomeet.notificationservice.controller;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.algomeet.notificationservice.dto.PushNotificationRequest;
import com.algomeet.notificationservice.enums.ResponseCode;
import com.algomeet.notificationservice.publisher.NotificationStreamPublisher;
import com.algomeet.notificationservice.response.CommonResponse;
import com.algomeet.notificationservice.util.LoggedInUserUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStreamPublisher notificationPublisher;
    
    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("notifications/push")
    public ResponseEntity<? extends CommonResponse<?>> create(@RequestBody @Valid PushNotificationRequest notificationRequest) throws JsonProcessingException {    	
    	if (!(StringUtils.hasLength(notificationRequest.getId()))) {
    		notificationRequest.setId(UUID.randomUUID().toString());
    	}
    	    	
    	if (!(StringUtils.hasLength(notificationRequest.getSenderId()))) {
    		notificationRequest.setSenderId(LoggedInUserUtil.getUsername());
    	}
    	
    	notificationPublisher.publish(objectMapper.writeValueAsString(notificationRequest));
    	
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS)); 
    }    

    @PostMapping("internal/notifications/push")
    public ResponseEntity<? extends CommonResponse<?>> internalCreate(@RequestBody @Valid PushNotificationRequest notificationRequest) throws JsonProcessingException { 
    	return create(notificationRequest);
    }
    
    @PostMapping("notifications/user/{userId}")
    public ResponseEntity<? extends CommonResponse<?>> getNotfications(@RequestBody @Valid PushNotificationRequest notificationRequest) throws JsonProcessingException { 
    	return null;
    }
}
