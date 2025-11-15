package com.algomeet.opaqueservice.controller;

import java.util.Base64;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.opaqueservice.dto.LoginFinalizeRequestDto;
import com.algomeet.opaqueservice.dto.LoginFinalizeResponseDto;
import com.algomeet.opaqueservice.dto.LoginStartRequestDto;
import com.algomeet.opaqueservice.dto.LoginStartResponseDto;
import com.algomeet.opaqueservice.dto.RegistrationRequestDto;
import com.algomeet.opaqueservice.dto.RegistrationResponseDto;
import com.algomeet.opaqueservice.dto.UserRecord;
import com.algomeet.opaqueservice.service.OpaqueLib;
import com.algomeet.opaqueservice.service.UserStore;

//---- Controller: registration & login ----
@RestController
@RequestMapping("/api/opaque")
class OpaqueController {
 private final UserStore store;
 private final byte[] serverSk; // server's OPAQUE long-term secret (persist securely)

 public OpaqueController(UserStore store){
     this.store = store;
     this.serverSk = ServerKeyManager.getServerSk(); // load from secure storage
 }

 // 1) Registration: client sends a registration message (derived from PIN locally)
 @PostMapping("/register")
 public ResponseEntity<RegistrationResponseDto> register(@RequestBody RegistrationRequestDto req) {
     byte[] clientRegMsg = Base64.getDecoder().decode(req.clientRegistrationMessageBase64());

     // Server processes the client's registration message and produces a registration record
     byte[] serverRegMsg = OpaqueLib.serverProcessRegistration(serverSk, clientRegMsg);

     // Create the registrationRecord we store for the user (opaque bytes)
     byte[] registrationRecord = OpaqueLib.makeRegistrationRecord(serverSk, clientRegMsg);

     // Persist only registrationRecord + server info. NOT the PIN.
     UserRecord r = new UserRecord(req.username(), registrationRecord, serverSk /* or server's info reference */);
     store.save(r);

     return ResponseEntity.ok(new RegistrationResponseDto(Base64.getEncoder().encodeToString(serverRegMsg)));
 }

 // 2a) Login start: client sends KE1 (derived locally from PIN) -> server responds KE2
 @PostMapping("/login/start")
 public ResponseEntity<LoginStartResponseDto> loginStart(@RequestBody LoginStartRequestDto req) {
     UserRecord r = store.get(req.username());
     if (r == null) {
         // For privacy, consider returning a valid-looking response or delaying uniformly.
         return ResponseEntity.status(401).build();
     }

     byte[] clientKe1 = Base64.getDecoder().decode(req.clientKe1Base64());
     byte[] serverKe2 = OpaqueLib.serverLoginStep2(serverSk, r.registrationRecord(), clientKe1);

     return ResponseEntity.ok(new LoginStartResponseDto(Base64.getEncoder().encodeToString(serverKe2)));
 }

 // 2b) Login finalize: client sends KE3; server verifies and then may return the protected envelope
 @PostMapping("/login/finalize")
 public ResponseEntity<LoginFinalizeResponseDto> loginFinalize(@RequestBody LoginFinalizeRequestDto req) {
     UserRecord r = store.get(req.username());
     if (r == null) return ResponseEntity.status(401).body(new LoginFinalizeResponseDto(false, null));

     byte[] clientKe3 = Base64.getDecoder().decode(req.clientKe3Base64());
     boolean ok = OpaqueLib.serverFinalize(serverSk, r.registrationRecord(), clientKe3);

     if (!ok) return ResponseEntity.ok(new LoginFinalizeResponseDto(false, null));

     // authentication succeeded. Prepare/provide user's envelope (e.g. encrypted syncKey)
     byte[] envelope = fetchUserEnvelopeFor(req.username()); // already encrypted and sealed server-side
     return ResponseEntity.ok(new LoginFinalizeResponseDto(true, Base64.getEncoder().encodeToString(envelope)));
 }

 private byte[] fetchUserEnvelopeFor(String username){
     // In practice, envelope contains an encrypted syncKey that client can decrypt with the OPAQUE-derived session key.
     return "demo-envelope-bytes".getBytes();
 }
}


//---- ServerKeyManager: load/generate server secret (persist securely) ----
class ServerKeyManager {
 public static byte[] getServerSk(){
     // load from HSM/KMS or config; DO NOT hardcode in real apps.
     return "server-secret-key-placeholder".getBytes();
 }
}