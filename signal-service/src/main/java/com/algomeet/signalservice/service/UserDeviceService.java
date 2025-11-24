package com.algomeet.signalservice.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.KyberPreKeyMapper;
import com.algomeet.signalservice.mapper.SignedPreKeyMapper;
import com.algomeet.signalservice.mapper.UserDeviceMapper;
import com.algomeet.signalservice.repository.KyberPreKeyRepository;
import com.algomeet.signalservice.repository.SignedPreKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserDeviceService {

	private final UserDeviceRepository repository;
	private final SignedPreKeyRepository signedPreKeyServiceRepository;
	private final KyberPreKeyRepository kyberPreKeyServiceRepository;

	public UserDeviceResponse createDevice(UUID userKey, UserDeviceRequest request) {
		// To generate new device ID, get maximum user device ID from table then increment it by 1.
		int deviceId = (repository.findMaxDeviceIdByUserKey(userKey).orElse(0) + 1);
		
		// Save device
		UserDevice device = UserDeviceMapper.toEntity(userKey, deviceId, request);		
		UserDevice savedDevice = repository.save(device);
		
		//Save prekeys
		SignedPreKey signedPreKey = SignedPreKeyMapper.toEntity(userKey, deviceId, request.getSignedPreKey());		
		SignedPreKey savedSignedPreKey= signedPreKeyServiceRepository.save(signedPreKey);
		
		// Save kyber		
		KyberPreKey kybePreKey = KyberPreKeyMapper.toEntity(userKey, deviceId, request.getKyberPreKey());		
		KyberPreKey savedKyberPreKey = kyberPreKeyServiceRepository.save(kybePreKey);
		
		// Construct response
		UserDeviceResponse userDeviceResponse = UserDeviceMapper.toResponse(savedDevice);
		userDeviceResponse.setSignedPreKey(SignedPreKeyMapper.toResponse(savedSignedPreKey));

		userDeviceResponse.setSignedPreKey(SignedPreKeyMapper.toResponse(savedSignedPreKey));
		userDeviceResponse.setKyberPreKey(KyberPreKeyMapper.toResponse(savedKyberPreKey));
		
		return userDeviceResponse;
	}

	public List<UserDeviceResponse> getDevicesByUser(UUID userKey) {
		return repository.findByIdUserKey(userKey)
				.stream()
				.map(UserDeviceMapper::toResponse)
				.collect(Collectors.toList());
	}

	public UserDeviceResponse updateDevice(UUID userKey, Integer deviceId, UserDeviceRequest request) {
		UserDevice updated = repository.findById(new UserDeviceId(userKey, deviceId)).map(device -> {
			if (StringUtils.hasLength(request.getIdentityKey())) {
				device.setIdentityKey(request.getIdentityKey());
			}

			if (request.getRegistrationId() != null) {
				device.setRegistrationId(request.getRegistrationId());
			}
			return repository.save(device);    		
		}).orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		return UserDeviceMapper.toResponse(updated);
	}

	public void deleteDevice(UUID userKey, Integer deviceId) {
		UserDeviceId id = new UserDeviceId(userKey, deviceId);
		repository.deleteById(id);
	}
}