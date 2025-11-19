package com.algomeet.opaqueservice.controller;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.LoginRequest;
import com.algomeet.opaqueservice.dto.LoginResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretRequest;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretResponse;
import com.algomeet.opaqueservice.dto.UserSecretRequest;
import com.algomeet.opaqueservice.dto.UserSecretResponse;
import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.enums.ResponseCode;
import com.algomeet.opaqueservice.jni.Opaque;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredReq;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueCreds;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;
import com.algomeet.opaqueservice.service.UserSecureStoreService;
import com.algomeet.opaqueservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/opaque")
@RequiredArgsConstructor
@Scope(value = WebApplicationContext.SCOPE_REQUEST)
class OpaqueController {
	private final UserSecureStoreService userSecureStoreService;

	@Value("${opaque.server.key}")
	private String serverKey;

	@Value("${opaque.server.id}")
	private String serverId;

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
		byte[] clientRegMsg = Base64.getDecoder().decode(req.getClientRegistrationMessage());
		OpaqueRegResp regResp = opaque.createRegResp(clientRegMsg, serverKey.getBytes(Charset.forName("UTF-8")));
		
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
				new RegistrationResponse(Base64.getEncoder().encodeToString(regResp.pub),
						Base64.getEncoder().encodeToString(regResp.sec),
						serverId)));
	}
	
	@PostMapping("/user/secret/store")
	public ResponseEntity<CommonResponse<UserSecretResponse>> saveSecret(@RequestBody UserSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());			
		byte[] rec = opaque.storeRec(Base64.getDecoder().decode(req.getServerSecretKey()), Base64.getDecoder().decode(req.getClientRecord()));
		
		UserSecureStore userSecureStore = userSecureStoreService.save(userKey, req.getType(), Base64.getEncoder().encodeToString(rec), req.getSecretKey());
		
		if (userSecureStore == null) {
			throw new RuntimeException("Error saving secret key");
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, UserSecretResponse.builder()
				.userKey(userSecureStore.getId().getUserKey())
				.type(userSecureStore.getId().getType())
				.secretKey(userSecureStore.getSecretKey())			
				.build())); 
	}	
	
	@PostMapping("/login")
	public ResponseEntity<CommonResponse<LoginResponse>> login(@RequestBody LoginRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserSecureStore userSecureStore = userSecureStoreService.getSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SECRET_KEY_NOT_FOUND)); 
		} 
		
		OpaqueIds ids = new OpaqueIds(userKey.toString().getBytes(Charset.forName("UTF-8")),
				serverId.getBytes(Charset.forName("UTF-8"))); 
        
		System.out.println("Record: " + userSecureStore.getRec());
		System.out.println("req.getClientPublicKey(): " + req.getClientPublicKey());
		
		OpaqueCredResp credResp = opaque.createCredResp(Base64.getDecoder().decode(req.getClientPublicKey()), 
				Base64.getDecoder().decode(userSecureStore.getRec()), ids, "context");
		
		System.out.println("sec: " + Base64.getEncoder().encodeToString(credResp.sec));
			
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, new LoginResponse(
				serverId,
				Base64.getEncoder().encodeToString(credResp.pub),
				Base64.getEncoder().encodeToString(credResp.sec)))); 
	}		
		
	@PostMapping("/user/secret/retrieve")
	public ResponseEntity<CommonResponse<RetrieveUserSecretResponse>> retrieveSecret(@RequestBody RetrieveUserSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserSecureStore userSecureStore = userSecureStoreService.getSecret(userKey, req.getType());
		if (userSecureStore == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SECRET_KEY_NOT_FOUND)); 
		} 
		
//		OpaqueIds ids = new OpaqueIds(userKey.toString().getBytes(Charset.forName("UTF-8")),
//				serverId.getBytes(Charset.forName("UTF-8")));
//		
//		OpaqueCredResp credResp = opaque.createCredResp(Base64.getDecoder().decode(req.getClientPublicKey()), 
//				Base64.getDecoder().decode(userSecureStore.getRec()), ids, "context");
//		
//		
//        OpaqueCreds creds = opaque.recoverCreds(credResp.pub, credResp.sec, "context", ids);
		
		System.out.println("getServerSecKey: " + req.getServerSecKey());
		System.out.println("getClientAuth: " + req.getClientAuth());
        
        if (opaque.userAuth(Base64.getDecoder().decode(req.getServerSecKey()), Base64.getDecoder().decode(req.getClientAuth()))) {        	
        	RetrieveUserSecretResponse resp = RetrieveUserSecretResponse.builder()
        			.userKey(userSecureStore.getId().getUserKey())
        			.type(userSecureStore.getId().getType())
        			.secretKey(userSecureStore.getSecretKey())
        			.build();

        	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, resp)); 
        }				

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
        		CommonResponse.from(ResponseCode.SECRET_KEY_FORBIDDEN_ACCESS)); 	
	}		
}