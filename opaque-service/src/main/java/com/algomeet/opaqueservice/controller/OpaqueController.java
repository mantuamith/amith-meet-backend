package com.algomeet.opaqueservice.controller;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.RetrieveUserMasterSecretResponse;
import com.algomeet.opaqueservice.dto.UserCredentialRequest;
import com.algomeet.opaqueservice.dto.UserCredentialResponse;
import com.algomeet.opaqueservice.dto.UserMasterSecretRequest;
import com.algomeet.opaqueservice.dto.UserMasterSecretResponse;
import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.enums.ResponseCode;
import com.algomeet.opaqueservice.jni.Opaque;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;
import com.algomeet.opaqueservice.service.OpaqueRedisKeysUtil;
import com.algomeet.opaqueservice.service.UserSecureStoreService;
import com.algomeet.opaqueservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/opaque")
@RequiredArgsConstructor
class OpaqueController {
	private final UserSecureStoreService userSecureStoreService;

	private final RedisTemplate<String, String> redisTemplate;


	@Value("${opaque.server.key}")
	private String serverKey;

	@Value("${opaque.server.id}")
	private String serverId;

	@Value("${opaque.server.credential.sec.ttl-in-minutes:60}")
	private Integer credentialSecTtl;

	@Autowired
	private Opaque opaque;

	/** 
	 * Registration: client sends a registration message (derived from PIN/Device secret locally)
	 * 
	 * @param req
	 * @return exportKey
	 */
	@PostMapping("/register")
	public ResponseEntity<CommonResponse<RegistrationResponse>> register(@RequestBody RegistrationRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());	

		byte[] clientRegMsg = Base64.getDecoder().decode(req.getClientRegistrationMessage());
		OpaqueRegResp regResp = opaque.createRegResp(clientRegMsg, serverKey.getBytes(Charset.forName("UTF-8")));

		String key = OpaqueRedisKeysUtil.getRegisterServerSecKey(userKey.toString(), req.getType());

		// Temporarily store the  server secret to redis
		redisTemplate.opsForValue().set(key, 
				Base64.getEncoder().encodeToString(regResp.sec),
				Duration.ofSeconds(credentialSecTtl));

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
				new RegistrationResponse(Base64.getEncoder().encodeToString(regResp.pub),
						serverId)));
	}

	@PostMapping("/user/master-secret/store")
	public ResponseEntity<CommonResponse<UserMasterSecretResponse>> saveSecret(@RequestBody UserMasterSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());	

		String key = OpaqueRedisKeysUtil.getRegisterServerSecKey(userKey.toString(), req.getType());

		byte[] rec = opaque.storeRec(
				Base64.getDecoder().decode(redisTemplate.opsForValue().get(key)), 
				Base64.getDecoder().decode(req.getClientRecord()));

		UserSecureStore userSecureStore = userSecureStoreService.save(userKey, req.getType(), 
				Base64.getEncoder().encodeToString(rec), 
				req.getMasterSecretKey());

		if (userSecureStore == null) {
			throw new RuntimeException("Error saving secret key");
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, UserMasterSecretResponse.builder()
				.userKey(userSecureStore.getId().getUserKey())
				.type(userSecureStore.getId().getType())
				.secretKey(userSecureStore.getMasterSecretKey())			
				.build())); 
	}	

	@PostMapping("/user/master-secret/credential")
	public ResponseEntity<CommonResponse<UserCredentialResponse>> athen(@RequestBody UserCredentialRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserSecureStore userSecureStore = userSecureStoreService.getSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SECRET_KEY_NOT_FOUND)); 
		} 

		OpaqueIds ids = new OpaqueIds(userKey.toString().getBytes(Charset.forName("UTF-8")),
				serverId.getBytes(Charset.forName("UTF-8"))); 

		OpaqueCredResp credResp = opaque.createCredResp(Base64.getDecoder().decode(req.getClientPublicKey()), 
				Base64.getDecoder().decode(userSecureStore.getRec()), ids, "context");

		String key = OpaqueRedisKeysUtil.getSecretCredentialServerSecKey(userKey.toString(), req.getType());

		// Temporarily store the  server secret to redis
		redisTemplate.opsForValue().set(key, 
				Base64.getEncoder().encodeToString(credResp.sec),
				Duration.ofSeconds(credentialSecTtl));

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, new UserCredentialResponse(
				serverId,
				Base64.getEncoder().encodeToString(credResp.pub)))); 
	}		

	@PostMapping("/user/master-secret/retrieve")
	public ResponseEntity<CommonResponse<RetrieveUserMasterSecretResponse>> retrieveSecret(@RequestBody RetrieveUserMasterSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		String key = OpaqueRedisKeysUtil.getSecretCredentialServerSecKey(userKey.toString(), req.getType());

		UserSecureStore userSecureStore = userSecureStoreService.getSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SECRET_KEY_NOT_FOUND)); 
		} 

		if (opaque.userAuth(Base64.getDecoder().decode(redisTemplate.opsForValue().get(key)), 
				Base64.getDecoder().decode(req.getClientAuth()))) {     

			RetrieveUserMasterSecretResponse resp = RetrieveUserMasterSecretResponse.builder()
					.userKey(userSecureStore.getId().getUserKey())
					.type(userSecureStore.getId().getType())
					.masterSecretKey(userSecureStore.getMasterSecretKey())
					.build();

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, resp)); 
		}				

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				CommonResponse.from(ResponseCode.SECRET_KEY_FORBIDDEN_ACCESS)); 	
	}		
}