package com.algomeet.opaqueservice.controller;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.opaqueservice.dto.CommonResponse;
import com.algomeet.opaqueservice.dto.RegistrationRequest;
import com.algomeet.opaqueservice.dto.RegistrationResponse;
import com.algomeet.opaqueservice.dto.RetrieveUserSecretRequest;
import com.algomeet.opaqueservice.dto.UserSecretRequest;
import com.algomeet.opaqueservice.dto.UserSecretResponse;
import com.algomeet.opaqueservice.entity.UserE2eeSecret;
import com.algomeet.opaqueservice.entity.UserOpaqueCredential;
import com.algomeet.opaqueservice.enums.ResponseCode;
import com.algomeet.opaqueservice.jni.Opaque;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueCreds;
import com.algomeet.opaqueservice.jni.dto.OpaqueIds;
import com.algomeet.opaqueservice.jni.dto.OpaquePreRecExpKey;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;
import com.algomeet.opaqueservice.service.UserE2eeSecretService;
import com.algomeet.opaqueservice.service.UserOpaqueCredentialService;
import com.algomeet.opaqueservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/opaque")
@RequiredArgsConstructor
class OpaqueController {
	private final UserOpaqueCredentialService opaqueCredentialService;
	private final UserE2eeSecretService userE2eeSecretService;

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
		String userKey = SecurityUtil.getUserKey();

		byte[] clientRegMsg = Base64.getDecoder().decode(req.getClientRegistrationMessageBase64());

		OpaqueRegResp regResp = opaque.createRegResp(clientRegMsg, serverKey.getBytes(Charset.forName("UTF-8")));

		OpaqueIds ids = new OpaqueIds(userKey.getBytes(Charset.forName("UTF-8")),
				serverId.getBytes(Charset.forName("UTF-8")));

		OpaquePreRecExpKey preRec = opaque.finalizeReg(regResp.sec, regResp.pub, ids);
		byte[] rec = opaque.storeRec(regResp.sec, preRec.rec); 

		opaqueCredentialService.saveOrUpdate(UUID.fromString(userKey), req.getType(), Base64.getEncoder().encodeToString(rec));

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
				new RegistrationResponse(Base64.getEncoder().encodeToString(preRec.export_key))));
	}

	@PostMapping("/user/secret")
	public ResponseEntity<CommonResponse<UserSecretResponse>> saveSecret(@RequestBody UserSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserE2eeSecret userE2eeSecret = userE2eeSecretService.save(userKey, req.getType(), req.getSecretKey());

		if (userE2eeSecret == null) {
			throw new RuntimeException("Error saving secret key");
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, UserSecretResponse.builder()
				.secretKey(userE2eeSecret.getSecretKey())
				.userKey(userE2eeSecret.getId().getUserKey())
				.type(userE2eeSecret.getId().getType())
				.build())); 
	}	
		
	@PostMapping("/user/secret/retrieve")
	public ResponseEntity<CommonResponse<UserSecretResponse>> retrieveSecret(@RequestBody RetrieveUserSecretRequest req) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		UserE2eeSecret userE2eeSecret = userE2eeSecretService.getSecret(userKey, req.getType());
		if (userE2eeSecret == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.SECRET_KEY_NOT_FOUND)); 
		} 
		
		OpaqueIds ids = new OpaqueIds(userKey.toString().getBytes(Charset.forName("UTF-8")),
				serverId.getBytes(Charset.forName("UTF-8")));
		
		UserOpaqueCredential opaqueCred = opaqueCredentialService.getCredential(userKey, req.getType());
		
		OpaqueCredResp credResp = opaque.createCredResp(Base64.getDecoder().decode(req.getPublicKey()), 
				Base64.getDecoder().decode(opaqueCred.getRec()), ids, "context");
        OpaqueCreds creds = opaque.recoverCreds(credResp.pub, credResp.sec, "context", ids);
        
        if (opaque.userAuth(credResp.sec, creds.authU)) {        	
        	UserSecretResponse resp = UserSecretResponse.builder()
        			.userKey(userKey)
        			.secretKey(userE2eeSecret.getSecretKey())
        			.exportKey(Base64.getEncoder().encodeToString(creds.export_key))
        			.build();

        	return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, resp)); 
        }				

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
        		CommonResponse.from(ResponseCode.SECRET_KEY_FORBIDDEN_ACCESS)); 	
	}		
}