package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.exception.UserAlreadyExistsException;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final PendingRegistrationRepository pendingRepo;
  private final PasswordEncoder passwordEncoder;   // BCrypt
  private final OtpService otpService;
  private final ObjectMapper objectMapper;// you already have

  private final UserClient userClient;
  private final AuthProperties props;

  public RegisterInitResponse init(RegisterInitRequest req, String ip) {
    if (!StringUtils.hasText(req.getEmail()) && !StringUtils.hasText(req.getPhone())) {
      throw new IllegalArgumentException("Either email or phone is required");
    }

    Map<String, Boolean> exists = userClient.checkExists(req.getEmail(), req.getUsername(), req.getPhone());
    checkForDuplicateUser(exists);

    String hash = passwordEncoder.encode(req.getPassword());

    String txn = UUID.randomUUID().toString();
    Instant now = Instant.now();
    long ttl = props.getOtp().getTtlSeconds(); // from AuthProperties
    Instant expireAt = now.plusSeconds(ttl);

    var doc = PendingRegistrationDoc.builder()
            .txn(txn)
            .username(req.getUsername())
            .email(req.getEmail())
            .phone(req.getPhone())
            .passwordHash(hash)
            .deviceId(req.getDeviceId())
            .deviceType(req.getDeviceType())
            .country(req.getCountry())
            .region(req.getRegion())
            .city(req.getCity())
            .latitude(req.getLatitude())
            .longitude(req.getLongitude())
            .createdAt(now)
            .expireAt(expireAt)
            .role(req.getRole())
            .tenantId(req.getTenantId())
            .build();

    pendingRepo.save(doc);

    String channel;
    if (StringUtils.hasText(req.getEmail())) {
      channel = "EMAIL";
      otpService.initEmailRegistrationOtp(req.getEmail());
    } else {
      channel = "SMS";
      otpService.initSmsRegistrationOtp(req.getPhone());
    }

    return new RegisterInitResponse(txn, channel, "OTP sent.");
  }

  private void checkForDuplicateUser(Map<String, Boolean> exists) {
    boolean emailTaken = Boolean.TRUE.equals(exists.get("emailTaken"));
    boolean userTaken  = Boolean.TRUE.equals(exists.get("usernameTaken"));
    boolean phoneTaken = Boolean.TRUE.equals(exists.get("phoneTaken"));

    if (emailTaken || userTaken || phoneTaken) {
      Set<String> fields = new LinkedHashSet<>(3);
      if (emailTaken) fields.add("email");
      if (userTaken)  fields.add("username");
      if (phoneTaken) fields.add("phone");

      String message;
      ResponseCode code;

      if (emailTaken && userTaken && phoneTaken) {
        message = "Email, username and phone already exist";
        code = ResponseCode.AUTH_DUPLICATE_BOTH; //
      } else if (emailTaken && userTaken) {
        message = "Email and username already exist";
        code = ResponseCode.AUTH_DUPLICATE_BOTH;
      } else if (emailTaken && phoneTaken) {
        message = "Email and phone already exist";
        code = ResponseCode.AUTH_DUPLICATE_BOTH;
      } else if (userTaken && phoneTaken) {
        message = "Username and phone already exist";
        code = ResponseCode.AUTH_DUPLICATE_BOTH;
      } else if (emailTaken) {
        message = "Email already exists";
        code = ResponseCode.AUTH_DUPLICATE_EMAIL;
      } else if (userTaken) {
        message = "Username already exists";
        code = ResponseCode.AUTH_DUPLICATE_USERNAME;
      } else {
        message = "Phone already exists";
        code = ResponseCode.AUTH_DUPLICATE_PHONE;
      }

      throw new UserAlreadyExistsException(message, fields, code);
    }
  }


  @Transactional(noRollbackFor = IllegalArgumentException.class)
  public RegisterVerifyResponse verify(RegisterVerifyRequest req, String ip) {
    // 1) Load pending txn
    PendingRegistrationDoc doc = pendingRepo.findById(req.getTransactionId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired registration txn"));

    // 2) Verify OTP using the stored channel
    boolean ok = StringUtils.hasText(doc.getEmail())
            ? otpService.verifyEmailRegistrationOtp(doc.getEmail(), req.getCode())
            : otpService.verifySmsRegistrationOtp(doc.getPhone(), req.getCode());
    if (!ok) throw new IllegalArgumentException("Invalid or expired OTP");

    // 3) Prepare create payload for user-service
    UserRequest create = UserRequest.builder()
            .email(doc.getEmail())
            .phone(doc.getPhone())
            .username(doc.getUsername())
            .password(doc.getPasswordHash()) // already BCrypted in pending doc
            .country(doc.getCountry())
            .region(doc.getRegion())
            .city(doc.getCity())
            .latitude(doc.getLatitude())
            .longitude(doc.getLongitude())
            .isEmailVerified(StringUtils.hasText(doc.getEmail()))
            .isPhoneVerified(StringUtils.hasText(doc.getPhone()))
            .registrationIp(ip)
            .registrationDeviceId(doc.getDeviceId())
            .registrationDeviceType(doc.getDeviceType() != null ? doc.getDeviceType().toString() : null)
            .loginTypePolicy(props.getAuth().getLoginTypePolicyDefault())
            .role(doc.getRole())
            .tenantId(doc.getTenantId())
            .build();

    // 4) Call user-service and unwrap its Map response safely
    Map<String, Object> resp;
    try {
      resp = userClient.createUser(create); // Feign returns Map
    } catch (feign.FeignException e) {
      String body = e.contentUTF8();
      throw new IllegalStateException("User-service create failed: " +
              (body == null ? e.getMessage() : body), e);
    }
    if (resp == null) throw new IllegalStateException("User-service returned null response");

    Object userNode = resp.get("user");
    if (userNode == null) {
      // Likely an error payload, surface message/code if present
      Object code = resp.get("code");
      Object msg  = resp.get("message");
      String details = (msg != null ? msg.toString() : "User-service did not return expected 'user' object");
      if (code != null) details += " [code=" + code + "]";
      throw new IllegalStateException(details);
    }

    // Convert nested map ->
    UserResponse created = objectMapper.convertValue(userNode, UserResponse.class);

    // 5) Best-effort: bind active device (ignore failures)
    try {
      if (created.getId() != null && doc.getDeviceId() != null) {
        userClient.updateActiveDevice(created.getId(), doc.getDeviceId());
      }
    } catch (Exception e) {
      log.warn("bind device failed for userId={} deviceId={} err={}",
              created.getId(), doc.getDeviceId(), e.toString());
    }

    // 6) Cleanup pending doc
    pendingRepo.deleteById(req.getTransactionId());

    // 7) Build final response
    String type = StringUtils.hasText(doc.getEmail()) ? "EMAIL" : "PHONE";
    return new RegisterVerifyResponse(type, "Registration successful.", created);
  }


}
