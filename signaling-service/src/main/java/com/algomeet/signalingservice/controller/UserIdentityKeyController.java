package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.algomeet.signalingservice.dto.*;
import com.algomeet.signalingservice.service.UserIdentityKeyService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signaling/identity-keys")
@RequiredArgsConstructor
public class UserIdentityKeyController {
	private final UserIdentityKeyService keyService;

	// Register a new user identity key
	@PostMapping
	public ResponseEntity<UserIdentityKeyResponse> registerUserIdentity(@RequestBody UserIdentityKeyRequest request) {
		return ResponseEntity.ok(keyService.registerUserIdentity(request));
	}

	// Add one-time key for existing user
	@PostMapping("/one-time-keys")
	public ResponseEntity<List<IdentityOneTimeKeyResponse>> addOneTimeKeys(@RequestBody IdentityOneTimeKeyRequest request) {
		return ResponseEntity.ok(keyService.addOneTimeKeys(request));
	}

	// Delete one-time key for existing user
	@DeleteMapping("/one-time-keys/{id}")
	public ResponseEntity<?> deleteOneTimeKey(@PathVariable Long id) {
		keyService.deleteOneTimeKey(id);
		return ResponseEntity.ok("");
	}

	// Get identity key and one-time key of a user
	@GetMapping("/user/{userKey}")
	public ResponseEntity<IdentityOneTimeKeyResponse> getUserOneTimeKey(@PathVariable UUID userKey) {
		return ResponseEntity.ok(keyService.getUserIdentityAndOneTimeKey(userKey));
	}
}