package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.UserDeviceControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.DevicePreKeyBundleResponse;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.DeviceExistsException;
import com.algomeet.signalservice.exceptions.DevicePreKeyBundleExistException;
import com.algomeet.signalservice.exceptions.OneTimePreKeyExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.UserDeviceService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal")
@RequiredArgsConstructor
public class UserDeviceController implements UserDeviceControllerDoc{
	private final UserDeviceService service;

	@Override
	@PostMapping("/v2/devices")
	public ResponseEntity<CommonResponse<UserDeviceResponse>> createDevice(@Validated @RequestBody UserDeviceRequest request) {
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							service.registerDevice(UUID.fromString(SecurityUtil.getUserKey()), request)));
			
		} catch (DeviceExistsException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_EXISTS));
		}
	}

	@Deprecated
	@Override
	@GetMapping("/v2/devices")
	public ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getDevices(@RequestParam Optional<UUID> userKey) {
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS,
						service.getDevicesByUserKeys(List.of(userKey.orElse(UUID.fromString(SecurityUtil.getUserKey()))))));
	}
	
	@Override
	@GetMapping("/v3/devices")
	public ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getDevicesV3(@RequestParam Optional<List<UUID>> userKeys) {
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS,
						service.getDevicesByUserKeys(userKeys.orElse(List.of(UUID.fromString(SecurityUtil.getUserKey()))))));
	}

	@Override
	@PutMapping("/v2/devices/{deviceId}")
	public ResponseEntity<CommonResponse<UserDeviceResponse>> updateDevice(@PathVariable Integer deviceId, @Validated @RequestBody UserDeviceRequest request) {  
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							service.updateDevice(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}

	@Override
	@DeleteMapping("/v2/devices/{deviceId}")
	public ResponseEntity<CommonResponse<?>> deleteDevice(@PathVariable Integer deviceId) {
		try {
			service.deleteDevice(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));			
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}

	@Override
	@PostMapping("/v2/devices/{deviceId}/keys")
	public ResponseEntity<CommonResponse<DevicePreKeyBundleResponse>> createDevicePreKeyBundle(@PathVariable Integer deviceId, 
			@Validated @RequestBody DevicePreKeyBundleRequest request) {
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							service.createDevicePreKeyBundle(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		} catch (OneTimePreKeyExistsException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					CommonResponse.from(ResponseCode.ONE_TIME_PRE_KEY_EXISTS));
		} catch (DevicePreKeyBundleExistException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					CommonResponse.from(ResponseCode.DEVICE_PRE_KEY_BUNDLE_EXISTS));
		}
	}
}