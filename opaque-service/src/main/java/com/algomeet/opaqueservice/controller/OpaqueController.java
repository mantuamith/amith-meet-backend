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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.opaqueservice.controller.swagger.OpaqueControllerDoc;
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

/**
 * REST controller implementing the OPAQUE (Oblivious Password Authentication and Key Exchange)
 * protocol flow for securing user master secrets and credential exchanges.
 * 
 * <p>This controller exposes the following stages of the OPAQUE authentication lifecycle:</p>
 * <ul>
 *     <li><b>Registration</b> – Client sends a registration message derived from a PIN/device secret.</li>
 *     <li><b>Master Secret Store</b> – Server persists the encrypted "record" (REC) produced by OPAQUE.</li>
 *     <li><b>Credential Response</b> – Server responds with its OPAQUE credential part for user authentication.</li>
 *     <li><b>Secret Retrieval</b> – User authenticates with OPAQUE and retrieves previously stored master secrets.</li>
 * </ul>
 * 
 * <p>Temporary server-side secrets are stored in Redis with configurable TTLs.</p>
 *
 * Endpoints are prefixed with <code>/opaque</code>.
 */
@RestController
@RequestMapping("/opaque")
@RequiredArgsConstructor
public class OpaqueController implements OpaqueControllerDoc{
	private final UserSecureStoreService userSecureStoreService;

	private final RedisTemplate<String, String> redisTemplate;

	@Value("${opaque.server.key}")
	private String serverKey;

	@Value("${opaque.server.id}")
	private String serverId;

	@Value("${opaque.server.credential.sec.ttl-in-minutes:60}")
	private Integer credentialSecTtl;
	
	@Value("${opaque.server.auth-attempts.limit:5}")
	private Integer authAttemptsLimit;
	
	@Value("${opaque.server.auth-attempts.ttl-in-minutes:5}")
	private Integer autAttemptsTtl;

	@Autowired
	private Opaque opaque;

	/**
     * Handles OPAQUE registration step 1.
     *
     * <p>The client first sends a registration message created locally from a PIN/device-secret–derived key.
     * The server responds with the server-side OPAQUE registration response and temporarily stores
     * the corresponding server "secret" in Redis.</p>
     *
     * @param req registration request containing client registration message and secret type
     * @return Base64-encoded server public registration response and server ID
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

	/**
     * Stores a user's encrypted master secret (OPAQUE "record") for the first time.
     *
     * <p>The client sends an encrypted record produced after completing OPAQUE registration.
     * Server re-computes the final record using the stored SEC and persists it to the database.</p>
     *
     * @param req master secret request object
     * @return stored master secret metadata
     */
	@PostMapping("/master-secret/store")
	public ResponseEntity<CommonResponse<UserMasterSecretResponse>> saveMasterSecret(@RequestBody UserMasterSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());	

		// Validate if master secret with same type already exist
		if (userSecureStoreService.getMasterSecret(userKey, req.getType()) != null){
			return ResponseEntity.status(HttpStatus.CONFLICT).body(
					CommonResponse.from(ResponseCode.MASTER_SECRET_KEY_ALRREADY_EXISTS)); 	
		}
		
		String key = OpaqueRedisKeysUtil.getRegisterServerSecKey(userKey.toString(), req.getType());

		byte[] rec = opaque.storeRec(
				Base64.getDecoder().decode(redisTemplate.opsForValue().get(key)), 
				Base64.getDecoder().decode(req.getRecord()));

		UserSecureStore userSecureStore = userSecureStoreService.save(userKey, Base64.getEncoder().encodeToString(rec), req);

		if (userSecureStore == null) {
			throw new RuntimeException("Error saving master secret key");
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, mapTo(userSecureStore))); 
	}	
	
	 /**
     * Updates an existing user's OPAQUE record and master secret metadata.
     *
     * @param req updated master secret request
     * @return updated master secret metadata
     */
	@PutMapping("/master-secret/store")
	public ResponseEntity<CommonResponse<UserMasterSecretResponse>> updateMasterSecret(@RequestBody UserMasterSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());		
		String key = OpaqueRedisKeysUtil.getRegisterServerSecKey(userKey.toString(), req.getType());

		byte[] rec = opaque.storeRec(
				Base64.getDecoder().decode(redisTemplate.opsForValue().get(key)), 
				Base64.getDecoder().decode(req.getRecord()));

		UserSecureStore userSecureStore = userSecureStoreService.save(userKey, Base64.getEncoder().encodeToString(rec), req);

		if (userSecureStore == null) {
			throw new RuntimeException("Error updating master secret key");
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, mapTo(userSecureStore))); 
	}	

	 /**
     * OPAQUE credential response step.
     *
     * <p>The client requests credential exchange using its ephemeral public key.
     * Server uses the stored OPAQUE "record" to produce the server credential response.
     * The server temporarily stores a "server secret" (SEC) used later for authentication.</p>
     *
     * @param req user credential request
     * @return server credential response containing server public key and server ID
     */
	@PostMapping("/credential-response")
	public ResponseEntity<CommonResponse<UserCredentialResponse>> exchangeMasterSecCredential(@RequestBody UserCredentialRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserSecureStore userSecureStore = userSecureStoreService.getMasterSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.MASTER_SECRET_KEY_NOT_FOUND)); 
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

	/**
     * Retrieves a user's master secret after successful OPAQUE authentication.
     *
     * <p>The client provides the final OPAQUE authentication message (ClientAuth).
     * If authentication succeeds, the server returns the stored master secret
     * and associated metadata.</p>
     *
     * @param req client authentication request
     * @return decrypted and validated master secret details
     */
	@PostMapping("/master-secret/retrieve")
	public ResponseEntity<CommonResponse<RetrieveUserMasterSecretResponse>> retrieveSecret(@RequestBody RetrieveUserMasterSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		String key = OpaqueRedisKeysUtil.getSecretCredentialServerSecKey(userKey.toString(), req.getType());

		UserSecureStore userSecureStore = userSecureStoreService.getMasterSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.MASTER_SECRET_KEY_NOT_FOUND)); 
		} 

		String maxAttemptsLockKey = OpaqueRedisKeysUtil.getAuthMaxAttemptsLockKey(userKey.toString(), req.getType());
		// Retrieve the attempts counter 
		String attemptsCounter = redisTemplate.opsForValue().get(maxAttemptsLockKey);			
		if (StringUtils.hasLength(attemptsCounter) && 
				Integer.parseInt(attemptsCounter) >= authAttemptsLimit) {

			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
					new CommonResponse<RetrieveUserMasterSecretResponse>(ResponseCode.MASTER_SECRET_KEY_TEMPORARILY_LOCKED.name(),
					ResponseCode.MASTER_SECRET_KEY_TEMPORARILY_LOCKED.getMessage().replace("{0}", attemptsCounter))); 
		}
		
		// Authenticate
		if (opaque.userAuth(Base64.getDecoder().decode(redisTemplate.opsForValue().get(key)), 
				Base64.getDecoder().decode(req.getClientAuth()))) {     

			RetrieveUserMasterSecretResponse resp = RetrieveUserMasterSecretResponse.builder()
					.userKey(userSecureStore.getId().getUserKey())
					.type(userSecureStore.getId().getType())
					.masterSecretKey(userSecureStore.getMasterSecretKey())
					.algorithm(userSecureStore.getAlgorithm())
					.version(userSecureStore.getVersion())
					.salt(userSecureStore.getSalt())
					.build();

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, resp)); 
		} else {
						
			redisTemplate.opsForValue().set(maxAttemptsLockKey,  
					Integer.toString(StringUtils.hasLength(attemptsCounter) ? (Integer.parseInt(attemptsCounter) + 1) : 1),
					Duration.ofSeconds(credentialSecTtl));
		}
		
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
				CommonResponse.from(ResponseCode.MASTER_SECRET_KEY_FORBIDDEN_ACCESS)); 	
	}		
	
	public UserMasterSecretResponse mapTo(UserSecureStore userSecureStore) {
		return UserMasterSecretResponse.builder()
		.userKey(userSecureStore.getId().getUserKey())
		.type(userSecureStore.getId().getType())
		.masterSecretKey(userSecureStore.getMasterSecretKey())	
		.algorithm(userSecureStore.getAlgorithm())
		.version(userSecureStore.getVersion())
		.salt(userSecureStore.getSalt())
		.build();
	}
}