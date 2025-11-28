package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.DeviceKeyResponse;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;

public class UserDeviceMapper {	
    public static UserDevice toEntity(UUID userKey, Integer deviceId, UserDeviceRequest dto) {
        UserDevice device = new UserDevice();
        UserDeviceId id = new UserDeviceId(userKey, deviceId);
        device.setId(id);
        device.setRegistrationId(dto.getRegistrationId());
        device.setIdentityKey(dto.getIdentityKey());
        
        return device;
    }

    public static UserDeviceResponse toResponse(UserDevice device) {
        UserDeviceResponse dto = new UserDeviceResponse();
        dto.setUserKey(device.getId().getUserKey());
        dto.setDeviceId(device.getId().getDeviceId());
        dto.setRegistrationId(device.getRegistrationId());
        dto.setIdentityKey(device.getIdentityKey());
        
        dto.setCreatedAt(device.getCreatedAt());
        dto.setUpdatedAt(device.getUpdatedAt());
        return dto;
    }
    
    public static DeviceKeyResponse toDeviceKeyResponse(UserDevice device) {
    	DeviceKeyResponse dto = new DeviceKeyResponse();
        dto.setUserKey(device.getId().getUserKey());
        dto.setDeviceId(device.getId().getDeviceId());
        dto.setRegistrationId(device.getRegistrationId());
        dto.setIdentityKey(device.getIdentityKey());
        
        dto.setCreatedAt(device.getCreatedAt());
        dto.setUpdatedAt(device.getUpdatedAt());
        return dto;
    }
}
