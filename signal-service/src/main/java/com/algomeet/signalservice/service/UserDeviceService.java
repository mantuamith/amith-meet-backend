package com.algomeet.signalservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.dto.DeviceKeyResponse;
import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.DevicePreKeyBundleResponse;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.DeviceKeyBackupId;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;
import com.algomeet.signalservice.entity.OneTimePreKey;
import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.SignedPreKeyId;
import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.enums.E2eeEventActionType;
import com.algomeet.signalservice.exceptions.DeviceExistsException;
import com.algomeet.signalservice.exceptions.OneTimePreKeyExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.KyberPreKeyMapper;
import com.algomeet.signalservice.mapper.OneTimePreKeyMapper;
import com.algomeet.signalservice.mapper.SignedPreKeyMapper;
import com.algomeet.signalservice.mapper.UserDeviceMapper;
import com.algomeet.signalservice.repository.DeviceKeyBackupRepository;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;
import com.algomeet.signalservice.repository.KyberPreKeyRepository;
import com.algomeet.signalservice.repository.OneTimePreKeyRepository;
import com.algomeet.signalservice.repository.SessionBackupRepository;
import com.algomeet.signalservice.repository.SignedPreKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDeviceService {
	private final UserDeviceRepository repository;
	private final SignedPreKeyRepository signedPreKeyRepository;
	private final KyberPreKeyRepository kyberPreKeyRepository;
	private final OneTimePreKeyRepository oneTimePreKeyRepository;
	private final SubscriberAsyncService subscriberAsyncService;
	private final GroupSenderKeyRepository groupSenderKeyRepository;
	private final SessionBackupRepository sessionBackupRepository;
	private final DeviceKeyBackupRepository deviceKeyBackupRepository;

	public UserDeviceResponse registerDevice(UUID userKey, UserDeviceRequest request) {
		// To generate new device ID, get maximum user device ID from table then increment it by 1.
		int deviceId = (repository.findMaxDeviceIdByUserKey(userKey).orElse(0) + 1);

		// Save device
		UserDevice device = UserDeviceMapper.toEntity(userKey, deviceId, request);	

		UserDevice savedDevice =  null;
		try {
			savedDevice = repository.save(device);
		} catch (DataIntegrityViolationException ex) {
			throw new DeviceExistsException(
					String.format("User device already exist with registration ID %d and identity key %s", 
							request.getRegistrationId(), request.getIdentityKey()));
		}

		// Construct response
		UserDeviceResponse userDeviceResponse = UserDeviceMapper.toResponse(savedDevice);

		return userDeviceResponse;
	}

	public List<UserDeviceResponse> getDevicesByUserKeys(List<UUID> userKeys) {
		return repository.findByIdUserKeyIn(userKeys)
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

		
		// publish signal event to subscribers
		subscriberAsyncService.publishEventAsync(userKey, deviceId, E2eeEventActionType.UPDATED);
				
		return UserDeviceMapper.toResponse(updated);
	}

	@Transactional
	public void deleteDevice(UUID userKey, Integer deviceId) {
		UserDeviceId id = new UserDeviceId(userKey, deviceId);		
		if (repository.findById(id).isEmpty()) {
			throw new RecordNotFoundException("User device ID not found");
		}

		SignedPreKeyId signedPreKeyId = new SignedPreKeyId(userKey, deviceId);
		signedPreKeyRepository.deleteById(signedPreKeyId);

		KyberPreKeyId kyberPreKeyId = new KyberPreKeyId(userKey, deviceId);
		kyberPreKeyRepository.deleteById(kyberPreKeyId);

		oneTimePreKeyRepository.deleteByUserKeyAndDeviceId(userKey, deviceId);
				
		groupSenderKeyRepository.deleteByIdReceiverUserKeyAndIdReceiverDeviceId(userKey, deviceId);
		
		// Delete backups
		sessionBackupRepository.deleteByIdUserKeyAndIdDeviceId(userKey, deviceId);		
		deviceKeyBackupRepository.deleteById(new DeviceKeyBackupId(userKey, deviceId));
		
		//Delete device
		repository.deleteById(id);	
		
		// publish signal event to subscribers
		subscriberAsyncService.publishEventAsync(userKey, deviceId, E2eeEventActionType.REMOVED);
	}

	public DevicePreKeyBundleResponse createDevicePreKeyBundle(UUID userKey, Integer deviceId, DevicePreKeyBundleRequest request) {
		if (repository.findById(new UserDeviceId(userKey, deviceId)).isEmpty()) {
			throw new RecordNotFoundException("User device ID not found");
		}

		//Save signed prekeys
		SignedPreKey signedPreKey = SignedPreKeyMapper.toEntity(userKey, deviceId, request.getSignedPreKey());		
		signedPreKey.setCreatedAt(Instant.now());
		SignedPreKey savedSignedPreKey= signedPreKeyRepository.save(signedPreKey);

		// Save kyber prekeys	
		KyberPreKey savedKyberPreKey = null;
		if (request.getKyberPreKey() != null) {
			KyberPreKey kybePreKey = KyberPreKeyMapper.toEntity(userKey, deviceId, request.getKyberPreKey());	
			kybePreKey.setCreatedAt(Instant.now());
			savedKyberPreKey = kyberPreKeyRepository.save(kybePreKey);
		}

		// Save ontime prekeys
		List<OneTimePreKey> otPreKeys = request.getOneTimePreKeys().stream().map(otp -> OneTimePreKeyMapper.toEntity(userKey, deviceId, otp)).toList();

		List<OneTimePreKey> savedOtPreKeys = null;
		try {
			savedOtPreKeys = oneTimePreKeyRepository.saveAll(otPreKeys);
		} catch (DataIntegrityViolationException ex) {
			throw new OneTimePreKeyExistsException(
					String.format("Device one-time pre key already exist"));
		}

		// Construct response
		DevicePreKeyBundleResponse preKeyBundleResp = new DevicePreKeyBundleResponse();
		preKeyBundleResp.setSignedPreKey(SignedPreKeyMapper.toResponse(savedSignedPreKey));
		preKeyBundleResp.setKyberPreKey(KyberPreKeyMapper.toResponse(savedKyberPreKey));
		preKeyBundleResp.setOneTimePreKeys(savedOtPreKeys.stream().map(OneTimePreKeyMapper::toResponse).toList());

		// publish signal event to subscribers
		subscriberAsyncService.publishEventAsync(userKey, deviceId, E2eeEventActionType.ADDED);
		
		return preKeyBundleResp;
	}

	@Transactional
	public List<DeviceKeyResponse> getDeviceKeys(UUID userKey, Optional<List<Integer>> deviceIds) {

		// Fetch all UserDevices efficiently, avoiding N+1 for SignedPreKey and KyberPreKey
		List<UserDevice> devices = getDevicesOptimized(userKey, deviceIds);

		
		if (devices.isEmpty()) {
			if (deviceIds.isPresent()) {
				throw new RecordNotFoundException("User device ID not found");
			} else {
				return new ArrayList<>();
			}
		}
		

		// Batch-find all necessary OneTimePreKeys (OPKs)
		// Create a list of all UserDeviceId keys needed for OPKs
		List<Integer> allDeviceIds = devices.stream()
				.map(device -> device.getId().getDeviceId())
				.toList();

		// Custom Repository method to find the first unused OPK for each device
		// This requires a custom query that groups by UserKey/DeviceId and orders/limits
		List<OneTimePreKey> preKeysToUse = oneTimePreKeyRepository.findFirstUnusedPreKeysByUserKeyAndDeviceIds(userKey, allDeviceIds);

		// Create a map for fast lookup: (UserKey + DeviceId) -> OneTimePreKey
		Map<UserDeviceId, OneTimePreKey> preKeyMap = preKeysToUse.stream()
				.collect(Collectors.toMap(
						opk -> new UserDeviceId(opk.getUserKey(), opk.getDeviceId()),
						opk -> opk
						));

		// Delete all consumed OPKs in a single batch operation
		if (!preKeysToUse.isEmpty()) {
			List<Long> preKeyIdsToDelete = preKeysToUse.stream()
					.map(OneTimePreKey::getId)
					.toList();

			oneTimePreKeyRepository.deleteByIdInBatch(preKeyIdsToDelete);
		}

		// Construct response in a single, efficient loop (no DB access inside)
		List<DeviceKeyResponse> listDeviceKeyResp = new ArrayList<>();

		for (UserDevice device : devices) {
			DeviceKeyResponse deviceKeyResp = UserDeviceMapper.toDeviceKeyResponse(device); 

			// SignedPreKey and KyberPreKey are already initialized due to Eager/JOIN FETCH
			deviceKeyResp.setSignedPreKey(SignedPreKeyMapper.toResponse(device.getSignedPreKey()));            
			deviceKeyResp.setKyberPreKey(KyberPreKeyMapper.toResponse(device.getKyberPreKey()));

			// Look up the consumed OPK from the pre-fetched map
			OneTimePreKey consumedPreKey = preKeyMap.get(device.getId());

			if (consumedPreKey != null) {
				deviceKeyResp.setOneTimePreKey(OneTimePreKeyMapper.toResponse(consumedPreKey));
			}

			listDeviceKeyResp.add(deviceKeyResp);
		}
		
		// Add signal keys subscriber, any signal keys update will be published to the subscriber
		subscriberAsyncService.addAsSubscriberAsync(userKey, UUID.fromString(SecurityUtil.getUserKey()));

		return listDeviceKeyResp;
	}

	@Transactional(readOnly = true)
	private List<UserDevice> getDevicesOptimized(UUID userKey, Optional<List<Integer>> deviceIds) {
		if (deviceIds.isPresent()) {
			return repository.findByIdUserKeyAndIdDeviceIdIn(userKey, deviceIds.get());
		} else {
			return repository.findByIdUserKey(userKey);
		}
	}

	/**
	 * Used to change the modified date of user device when signed or kyber pre-key has been modified. 
	 * This will be use as reference for the client to update their prekeys.
	 * 
	 * @param userKey
	 * @param deviceId
	 */
	public void markDeviceAsUpdated(UUID userKey, Integer deviceId) {
		UserDevice userDevice = repository.findById(new UserDeviceId(userKey, deviceId))
				.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));
		userDevice.setUpdatedAt(Instant.now());
		repository.save(userDevice);
		
		// publish signal event to subscribers
		subscriberAsyncService.publishEventAsync(userKey, deviceId, E2eeEventActionType.UPDATED);
	}
}