package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;


public class KyberPreKeyMapper {


	public static KyberPreKey toEntity(UUID userKey, Integer deviceId, KyberPreKeyRequest request) {
		KyberPreKey entity = new KyberPreKey();
		
		KyberPreKeyId id = new KyberPreKeyId(userKey, deviceId);
		entity.setId(id);
		entity.setKyberPreKeyId(request.getKyberPreKeyId());
		entity.setPublicKey(request.getPublicKey());
		entity.setSignature(request.getSignature());
		entity.setCreatedAt(java.time.Instant.now());
		return entity;
	}


	public static KyberPreKeyResponse toResponse(KyberPreKey entity) {
		if (entity == null) {
    		return null;
    	}
		
		return new KyberPreKeyResponse(
				entity.getId().getUserKey().toString(),
				entity.getId().getDeviceId(),
				entity.getKyberPreKeyId(),
				entity.getPublicKey(),
				entity.getSignature(),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
				);
	}
}