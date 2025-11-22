package com.algomeet.signalservice.mapper;

import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;


public class KyberPreKeyMapper {


	public static KyberPreKey toEntity(KyberPreKeyId id, KyberPreKeyRequest request) {
		KyberPreKey entity = new KyberPreKey();
		entity.setId(id);
		entity.setKyberPreKeyId(request.getKyberPreKeyId());
		entity.setPublicKey(request.getPublicKey());
		entity.setSignature(request.getSignature());
		entity.setCreatedAt(java.time.Instant.now());
		return entity;
	}


	public static KyberPreKeyResponse toResponse(KyberPreKey entity) {
		return new KyberPreKeyResponse(
				entity.getId().getUserKey().toString(),
				entity.getId().getDeviceId(),
				entity.getKyberPreKeyId(),
				entity.getPublicKey(),
				entity.getSignature(),
				entity.getCreatedAt()
				);
	}
}