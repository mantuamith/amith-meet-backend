package com.algomeet.signalservice.mapper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.entity.OneTimePreKey;

@Component
public class OneTimePreKeyMapper {


	public static OneTimePreKey toEntity(UUID userKey, Integer deviceId, OneTimePreKeyRequest request) {
		OneTimePreKey e = new OneTimePreKey();
		e.setUserKey(userKey);
		e.setDeviceId(deviceId);
		e.setPreKeyId(request.getPreKeyId());
		e.setPublicKey(request.getPublicKey());
		e.setUsed(false);
		return e;
	}


	public static OneTimePreKeyResponse toResponse(OneTimePreKey e) {
		if (e == null) {
    		return null;
    	}
		return new OneTimePreKeyResponse(
				e.getId(),
				e.getUserKey(),
				e.getDeviceId(),
				e.getPreKeyId(),
				e.getPublicKey(),
				e.getUsed(),
				e.getCreatedAt()
				);
	}
}
