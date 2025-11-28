package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.KeyControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DeviceKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.UserDeviceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signal/v2/keys")
@RequiredArgsConstructor
public class KeyController implements KeyControllerDoc{
	private final UserDeviceService service;

	@Override
	@GetMapping("/{userKey}")
	public ResponseEntity<CommonResponse<List<DeviceKeyResponse>>> getKeys(@PathVariable UUID userKey, 
			@RequestParam Optional<List<Integer>> deviceIds) {
		try {
			return ResponseEntity.ok(
					CommonResponse.from(ResponseCode.SUCCESS,
							service.getDeviceKeys(userKey,  deviceIds)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}
}