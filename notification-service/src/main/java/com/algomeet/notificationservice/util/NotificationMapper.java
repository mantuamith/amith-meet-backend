package com.algomeet.notificationservice.util;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.model.Notification;

public class NotificationMapper {

    public static NotificationDto toDto(Notification notification) {
        if (notification == null) return null;

        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        
        if (Objects.nonNull(notification.getType())) {
        	dto.setType(notification.getType());
        }
        dto.setTitle(notification.getTitle());
        dto.setBody(notification.getBody());
        dto.setSenderId(notification.getSenderId());
        
        dto.setReceiverGroup(notification.getReceiverGroup());
        dto.setReceiverGroupRefId(notification.getReceiverGroupRefId());

        
        if (StringUtils.hasLength(notification.getReceiverId())) {
        	dto.setReceiverIds(Set.of(notification.getReceiverId().split(Constants.MULTIPLE_RECEIVER_ID_DELIMITER)));
        }

        dto.setCreatedAt(notification.getCreatedAt());
        dto.setExpiredAt(notification.getExpiredAt());

        dto.setData(notification.getData());

        return dto;
    }

    public static Notification toEntity(NotificationDto dto) {
        if (dto == null) return null;

        Notification notification = new Notification();
        notification.setId(dto.getId());
        if(Objects.nonNull(dto.getType())) {
          notification.setType(dto.getType());
        }
        notification.setTitle(dto.getTitle());
        notification.setBody(dto.getBody());
        notification.setSenderId(dto.getSenderId());
        
       	notification.setReceiverGroup(dto.getReceiverGroup());
       	notification.setReceiverGroupRefId(dto.getReceiverGroupRefId());
        
        if(!CollectionUtils.isEmpty(dto.getReceiverIds())) {
        	notification.setReceiverId(dto.getReceiverIds().stream()
                .collect(Collectors.joining(Constants.MULTIPLE_RECEIVER_ID_DELIMITER)));
        }
        
        notification.setCreatedAt(dto.getCreatedAt());
        notification.setExpiredAt(dto.getExpiredAt());
        
        notification.setData(dto.getData());

        return notification;
    }
}
